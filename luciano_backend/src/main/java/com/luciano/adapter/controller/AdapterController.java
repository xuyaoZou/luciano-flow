package com.luciano.adapter.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.adapter.*;
import com.luciano.adapter.dto.GenerateRequest;
import com.luciano.adapter.dto.GenerateResponse;
import com.luciano.adapter.dto.RouteRequest;
import com.luciano.adapter.dto.TaskStatusResponse;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.GenerationTask;
import com.luciano.service.ApiOperationLogService;
import com.luciano.entity.ApiOperationLog;
import com.luciano.repository.mapper.UserMapper;
import com.luciano.entity.MediaAsset;
import com.luciano.service.GenerationTaskService;
import com.luciano.service.MediaAssetService;
import com.luciano.service.MediaDownloadService;
import com.luciano.service.StorageProviderService;
import com.luciano.storage.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 适配器统一 API
 * <p>
 * 所有模型生成请求统一走此 Controller，
 * 由 AdapterRegistry 路由到具体适配器。
 * <p>
 * 持久化策略：
 * - generate() 提交时写入 generation_tasks（status=SUBMITTED）
 * - pollTask() COMPLETED 时自动下载文件 + 写 media_assets
 */
@RestController
@RequestMapping("/api/v1/adapters")
@RequiredArgsConstructor
@Slf4j
public class AdapterController {

    private final AdapterRegistry registry;
    private final GenerationTaskService generationTaskService;
    private final MediaAssetService mediaAssetService;
    private final MediaDownloadService mediaDownloadService;
    private final StorageService storageService;
    private final StorageProviderService storageProviderService;
    private final ObjectMapper objectMapper;
    private final ApiOperationLogService operationLogService;
    private final UserMapper userMapper;
    private final com.luciano.adapter.kling.KlingApiClient klingApiClient;

    // ==================== 能力矩阵 ====================

    /**
     * 获取所有适配器列表
     */
    @GetMapping
    public ResponseEntity<?> listAdapters() {
        var adapters = registry.getAllAdapters().stream()
                .map(a -> Map.of(
                        "id", a.getId(),
                        "name", a.getDisplayName(),
                        "capabilities", a.getCapabilities().stream()
                                .map(Capability::getCode)
                                .toList()
                ))
                .toList();
        return ResponseEntity.ok(adapters);
    }

    /**
     * 获取能力矩阵（前端"选择模型"页面数据源）
     */
    @GetMapping("/capabilities")
    public ResponseEntity<CapabilityMatrix> getCapabilities() {
        return ResponseEntity.ok(registry.getCapabilityMatrix());
    }

    /**
     * 获取某个适配器的某个能力的参数 Schema
     */
    @GetMapping("/{adapterId}/schema/{capabilityCode}")
    public ResponseEntity<CapabilitySchema> getSchema(
            @PathVariable String adapterId,
            @PathVariable String capabilityCode,
            @RequestParam(required = false) String model) {

        ModelAdapter adapter = registry.getAdapter(adapterId);
        Capability capability = Capability.fromCode(capabilityCode);

        if (!adapter.supports(capability)) {
            return ResponseEntity.badRequest().build();
        }

        CapabilitySchema schema = adapter.getSchema(capability, model);
        if (schema == null) {
            return ResponseEntity.notFound().build();
        }

        markRequiredFlags(schema);
        return ResponseEntity.ok(schema);
    }

    /**
     * 获取某个适配器的所有能力 Schema
     */
    @GetMapping("/{adapterId}/schemas")
    public ResponseEntity<Map<String, CapabilitySchema>> getSchemas(
            @PathVariable String adapterId) {

        ModelAdapter adapter = registry.getAdapter(adapterId);
        Map<String, CapabilitySchema> schemas = new java.util.HashMap<>();

        for (Capability cap : adapter.getCapabilities()) {
            CapabilitySchema schema = adapter.getSchema(cap);
            if (schema != null) {
                // 确保 requiredParams 里的参数 required=true，optionalParams 里的 required=false
                markRequiredFlags(schema);
                schemas.put(cap.getCode(), schema);
            }
        }

        return ResponseEntity.ok(schemas);
    }

    // ==================== 提交任务 ====================

    /**
     * 提交生成任务
     * 同时写入 generation_tasks 表记录 + API 操作日志
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(
            @Valid @RequestBody GenerateRequest request,
            Authentication authentication) {

        log.info("[AdapterController] Generate request: adapterId={}, capability={}, projectId={}, params keys={}",
                request.getAdapterId(), request.getCapability(), request.getProjectIdAsLong(),
                request.getParams() != null ? request.getParams().keySet() : "null");

        // 设置操作日志上下文
        Long userId = null;
        String username = null;
        if (authentication != null && authentication.getPrincipal() instanceof Long uid) {
            userId = uid;
        }
        // 从 Authentication 中提取 username
        if (authentication != null && authentication.getDetails() instanceof java.util.Map details) {
            username = (String) details.get("username");
        }
        // 如果 details 里没有，尝试从 principal 名称获取
        if (username == null && authentication != null && authentication.getName() != null) {
            username = authentication.getName();
        }
        ModelHttpClient.LogContext.set(new ModelHttpClient.LogContext()
                .userId(userId)
                .username(username != null ? username : "unknown")
                .adapterId(request.getAdapterId())
                .capability(request.getCapability())
                .operationType("submit")
                .projectId(request.getProjectIdAsLong()));

        // 将 params 中的本地 URL 转成公网 URL（Kling 等 API 需要公网可访问的 URL）
        Map<String, Object> resolvedParams = resolveLocalUrls(request.getParams());
        request.setParams(resolvedParams);

        ModelAdapter adapter = registry.getAdapter(request.getAdapterId());
        Capability capability = Capability.fromCode(request.getCapability());

        if (!adapter.supports(capability)) {
            return ResponseEntity.badRequest().build();
        }

        // 参数校验
        ValidationResult validation = adapter.validate(capability, request.getParams());
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        // 提交到厂商
        TaskHandle handle = adapter.submit(capability, request.getParams());

        // 费用预估
        CostEstimate cost = null;
        try {
            cost = adapter.estimateCost(capability, request.getParams());
        } catch (Exception e) {
            log.warn("[AdapterController] Failed to estimate cost for {}/{}: {}",
                    request.getAdapterId(), request.getCapability(), e.getMessage());
        }

        // 获取当前用户 ID
        // userId 已在上面 LogContext 设置时获取

        // 先写入 generation_tasks 表获取 dbId
        GenerationTask dbTask = new GenerationTask();
        dbTask.setProjectId(request.getProjectIdAsLong());
        dbTask.setUserId(userId);
        dbTask.setTaskType(capability.getCode());
        dbTask.setGenerationMode("adapter");
        dbTask.setProvider(adapter.getId());
        dbTask.setAdapterId(adapter.getId());
        dbTask.setCapability(capability.getCode());
        dbTask.setStatus("submitted");
        dbTask.setProviderSource("platform");
        dbTask.setCreditsCost(0);  // TODO: 积分体系后从 cost 换算

        // 存参数到 config 字段
        try {
            dbTask.setConfig(objectMapper.writeValueAsString(request.getParams()));
        } catch (JsonProcessingException e) {
            dbTask.setConfig("{}");
        }

        // 存 prompt（如果有）
        if (request.getParams() != null) {
            Object prompt = request.getParams().get("prompt");
            if (prompt instanceof String p) {
                dbTask.setPrompt(p.length() > 500 ? p.substring(0, 500) : p);
            }
        }

        generationTaskService.save(dbTask);

        // taskId 格式: adapterId:capability:providerTaskId（同步结果时用 sync-dbId 代替 providerTaskId）
        String providerTaskIdForComposite = handle.getProviderTaskId() != null
                ? handle.getProviderTaskId() : "sync-" + dbTask.getId();
        String compositeTaskId = adapter.getId() + ":" + capability.getCode() + ":" + providerTaskIdForComposite;

        // 回填 taskId 到 dbTask
        dbTask.setTaskId(compositeTaskId);
        generationTaskService.updateById(dbTask);

        log.info("[AdapterController] Task saved: dbId={}, compositeTaskId={}, adapter={}, cap={}",
                dbTask.getId(), compositeTaskId, adapter.getId(), capability.getCode());

        // 更新日志的 taskId 和 providerTaskId
        operationLogService.updateTaskIdByContext(compositeTaskId, handle.getProviderTaskId(),
                request.getAdapterId(), request.getCapability(), "submit", userId);

        // 同步返回结果处理（如 Seedance 文生图）
        if (handle.getResultUrl() != null) {
            dbTask.setOutputUrl(handle.getResultUrl());
            dbTask.setStatus("completed");
            dbTask.setCompletedAt(OffsetDateTime.now());
            generationTaskService.updateById(dbTask);
            log.info("[AdapterController] Sync result: dbId={}, outputUrl={}", dbTask.getId(),
                    handle.getResultUrl().length() > 80 ? handle.getResultUrl().substring(0, 80) + "..." : handle.getResultUrl());

            // 创建 media_asset + 下载
            try {
                boolean isVideo = handle.getCapability() != null && handle.getCapability().name().contains("VIDEO");
                String mediaType = isVideo ? "video" : "image";
                Long projectId = dbTask.getProjectId();
                Long uid = dbTask.getUserId();

                MediaAsset asset = mediaAssetService.addAsset(
                        projectId != null ? projectId : 0L,
                        uid != null ? uid : 0L,
                        null,  // conversationId
                        adapter.getId(),  // source
                        mediaType,
                        handle.getResultUrl(),
                        null,  // thumbnailUrl
                        buildMetadata(dbTask, null, handle),
                        "sync-" + dbTask.getId(),  // runId
                        null   // agentMessageId
                );

                String localPath = mediaDownloadService.downloadToLocal(asset);
                if (localPath != null) {
                    asset.setLocalPath(localPath);
                    mediaAssetService.updateById(asset);
                    log.info("[AdapterController] Sync download OK: assetId={}, localPath={}", asset.getId(), localPath);
                }

                dbTask.setAssetId(asset.getId());
                generationTaskService.updateById(dbTask);
            } catch (Exception e) {
                log.warn("[AdapterController] Sync download failed: {}", e.getMessage());
            }

            // 同步完成：写一条 complete 操作日志（含平台状态）
            ApiOperationLog completeLog = new ApiOperationLog();
            completeLog.setUserId(userId);
            completeLog.setUsername(username != null ? username : "unknown");
            completeLog.setOperationType("complete");
            completeLog.setAdapterId(adapter.getId());
            completeLog.setCapability(capability.getCode());
            completeLog.setTaskId(compositeTaskId);
            completeLog.setProviderTaskId(handle.getProviderTaskId());
            completeLog.setProjectId(request.getProjectIdAsLong());
            completeLog.setPlatformStatus("succeeded");  // 同步返回 = 已成功
            completeLog.setResponseStatus(200);
            completeLog.setDurationMs(0);
            completeLog.setResultUrl(handle.getResultUrl() != null && handle.getResultUrl().length() > 2000
                    ? handle.getResultUrl().substring(0, 2000) : handle.getResultUrl());
            completeLog.setCreatedAt(OffsetDateTime.now());
            operationLogService.saveAsync(completeLog);
        }

        try {
            return ResponseEntity.ok(GenerateResponse.builder()
                    .taskId(compositeTaskId)
                    .dbTaskId(dbTask.getId())
                    .adapterId(handle.getAdapterId())
                    .capability(capability.getCode())
                    .providerTaskId(handle.getProviderTaskId())
                    .status(handle.getResultUrl() != null ? "COMPLETED" : "SUBMITTED")
                    .costHint(cost != null ? cost.getDisplayText() : null)
                    .build());
        } finally {
            ModelHttpClient.LogContext.clear();
        }
    }

    // ==================== 轮询任务 ====================

    /**
     * 查询任务状态
     * COMPLETED 时自动下载文件 + 写 media_assets
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskStatusResponse> pollTask(@PathVariable String taskId) {
        // taskId 格式: adapterId:capability:providerTaskId 或 旧格式 adapterId:providerTaskId
        String[] parts = taskId.split(":", 3);
        String adapterId;
        Capability capability = null;
        String providerTaskId;

        if (parts.length == 3) {
            adapterId = parts[0];
            try { capability = Capability.fromCode(parts[1]); } catch (Exception ignored) {}
            providerTaskId = parts[2];
        } else if (parts.length == 2) {
            adapterId = parts[0];
            providerTaskId = parts[1];
        } else {
            return ResponseEntity.badRequest().build();
        }

        // 设置轮询日志上下文
        ModelHttpClient.LogContext.set(new ModelHttpClient.LogContext()
                .adapterId(adapterId)
                .capability(capability != null ? capability.getCode() : null)
                .operationType("poll")
                .taskId(taskId)
                .providerTaskId(providerTaskId));

        ModelAdapter adapter = registry.getAdapter(adapterId);

        TaskHandle.TaskHandleBuilder handleBuilder = TaskHandle.builder()
                .taskId(taskId)
                .adapterId(adapterId)
                .providerTaskId(providerTaskId);
        if (capability != null) {
            handleBuilder.capability(capability);
        }
        TaskHandle handle = handleBuilder.build();

        TaskStatus status = adapter.poll(handle);

        // 回填平台原始状态到日志上下文
        if (handle.getPlatformStatus() != null) {
            ModelHttpClient.LogContext.current().platformStatus(handle.getPlatformStatus());
        }

        TaskStatusResponse.TaskStatusResponseBuilder responseBuilder = TaskStatusResponse.builder()
                .taskId(taskId)
                .adapterId(adapterId)
                .status(status.name());

        // 查找关联的 generation_task
        GenerationTask dbTask = findDbTask(taskId);
        if (dbTask != null) {
            responseBuilder.dbTaskId(dbTask.getId());
        }

        // 完成时下载结果 + 创建 media_asset（防重复：只处理一次）
        if (status == TaskStatus.COMPLETED && dbTask != null && "completed".equals(dbTask.getStatus())) {
            // dbTask 已经是 completed 状态，说明已经处理过了，跳过
            log.debug("[AdapterController] Task {} already completed, skipping asset creation", taskId);
            responseBuilder.mediaAssetId(findExistingAssetId(dbTask));
        } else if (status == TaskStatus.COMPLETED) {
            try {
                MediaResult result = adapter.download(handle);

                // 下载到本地
                String localPath = null;
                String mediaType = result.getMediaType() != null ? result.getMediaType() : "image";

                // 创建 media_asset 记录
                if (dbTask != null && result.getOriginalUrl() != null) {
                    Long projectId = dbTask.getProjectId();
                    Long userId = dbTask.getUserId();

                    MediaAsset asset = mediaAssetService.addAsset(
                            projectId != null ? projectId : 0L,
                            userId != null ? userId : 0L,
                            null,  // conversationId — adapter 生成不关联会话
                            adapterId,  // source = adapter id
                            mediaType,
                            result.getOriginalUrl(),
                            null,  // thumbnailUrl
                            buildMetadata(dbTask, result, handle),
                            null,  // runId — adapter 生成不用 runId
                            null   // agentMessageId
                    );

                    // 下载到本地存储
                    localPath = mediaDownloadService.downloadToLocal(asset);
                    if (localPath != null) {
                        asset.setLocalPath(localPath);
                        mediaAssetService.updateById(asset);
                        log.info("[AdapterController] Downloaded: assetId={}, localPath={}", asset.getId(), localPath);
                    }

                    responseBuilder.mediaAssetId(asset.getId());
                    log.info("[AdapterController] Media asset created: id={}, type={}, url={}",
                            asset.getId(), mediaType, result.getOriginalUrl());

                    // 回填 assetId 到 generation_task
                    dbTask.setAssetId(asset.getId());
                }

                // 更新 generation_task 状态
                if (dbTask != null) {
                    dbTask.setStatus("completed");
                    dbTask.setOutputUrl(result.getOriginalUrl());
                    dbTask.setOutputPath(localPath);
                    dbTask.setCompletedAt(OffsetDateTime.now());
                    generationTaskService.updateById(dbTask);
                }

                responseBuilder
                        .resultUrl(result.getOriginalUrl())
                        .localPath(localPath)
                        .durationMs(result.getDurationMs())
                        .resolution(result.getResolution());

                // 尾帧 URL（return_last_frame=true 时）
                if (result.getMetadata() != null && result.getMetadata().containsKey("last_frame_url")) {
                    responseBuilder.lastFrameUrl((String) result.getMetadata().get("last_frame_url"));
                }

            } catch (Exception e) {
                log.error("[AdapterController] Failed to process completed task {}: {}", taskId, e.getMessage(), e);

                // 即使下载失败，仍然更新 generation_task 为 completed
                if (dbTask != null) {
                    dbTask.setStatus("completed");
                    dbTask.setCompletedAt(OffsetDateTime.now());
                    generationTaskService.updateById(dbTask);
                }

                responseBuilder.status(TaskStatus.COMPLETED.name());
            }

                } else if (status == TaskStatus.FAILED) {
            // 更新 generation_task 为 failed
            if (dbTask != null) {
                dbTask.setStatus("failed");
                dbTask.setCompletedAt(OffsetDateTime.now());
                generationTaskService.updateById(dbTask);
            }
        } else {
            // 处理中 — 更新 generation_task 状态为 processing（如果之前是 submitted）
            if (dbTask != null && "submitted".equals(dbTask.getStatus())) {
                dbTask.setStatus("processing");
                generationTaskService.updateById(dbTask);
            }
        }

        // 任务完成或失败时，额外写一条 complete/failed 操作日志（包含平台状态）
        if ((status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) && dbTask != null) {
            String opType = status == TaskStatus.COMPLETED ? "complete" : "failed";
            ApiOperationLog completeLog = new ApiOperationLog();
            completeLog.setUserId(dbTask.getUserId());
            // 从 user_id 查 username
            if (dbTask.getUserId() != null) {
                try {
                    var user = userMapper.selectById(dbTask.getUserId());
                    if (user != null) {
                        completeLog.setUsername(user.getUsername());
                    }
                } catch (Exception e) {
                    log.debug("[AdapterController] Could not fetch username for userId={}: {}", dbTask.getUserId(), e.getMessage());
                }
            }
            completeLog.setOperationType(opType);
            completeLog.setAdapterId(adapterId);
            completeLog.setCapability(capability != null ? capability.getCode() : null);
            completeLog.setTaskId(taskId);
            completeLog.setProviderTaskId(providerTaskId);
            completeLog.setProjectId(dbTask.getProjectId());
            completeLog.setPlatformStatus(handle.getPlatformStatus());
            completeLog.setResponseStatus(200);
            completeLog.setDurationMs(0);
            if (status == TaskStatus.COMPLETED && dbTask.getOutputUrl() != null) {
                completeLog.setResultUrl(dbTask.getOutputUrl().length() > 2000
                        ? dbTask.getOutputUrl().substring(0, 2000) : dbTask.getOutputUrl());
            }
            completeLog.setCreatedAt(OffsetDateTime.now());
            operationLogService.saveAsync(completeLog);
        }

        try {
            return ResponseEntity.ok(responseBuilder.build());
        } finally {
            ModelHttpClient.LogContext.clear();
        }
    }

    // ==================== 智能路由 ====================

    /**
     * 智能路由：根据能力 + 偏好推荐适配器
     */
    @PostMapping("/route")
    public ResponseEntity<Map<String, Object>> route(@RequestBody RouteRequest request) {

        Capability capability = Capability.fromCode(request.getCapability());

        RoutePreference.RoutePreferenceBuilder prefBuilder = RoutePreference.builder();
        if (request.getPreference() != null) {
            prefBuilder
                    .preferredAdapter(request.getPreference().getPreferredAdapter())
                    .priority(request.getPreference().getPriority() != null
                            ? RoutePreference.Priority.valueOf(request.getPreference().getPriority())
                            : RoutePreference.Priority.QUALITY)
                    .maxBudgetFen(request.getPreference().getMaxBudgetFen());
        }

        ModelAdapter adapter = registry.route(capability, prefBuilder.build());

        return ResponseEntity.ok(Map.of(
                "adapterId", adapter.getId(),
                "displayName", adapter.getDisplayName(),
                "capability", capability.getCode()
        ));
    }

    // ==================== 参数校验 ====================

    /**
     * 校验参数（提交前预检）
     */
    @PostMapping("/{adapterId}/validate/{capabilityCode}")
    public ResponseEntity<ValidationResult> validate(
            @PathVariable String adapterId,
            @PathVariable String capabilityCode,
            @RequestBody Map<String, Object> params) {

        ModelAdapter adapter = registry.getAdapter(adapterId);
        Capability capability = Capability.fromCode(capabilityCode);

        if (!adapter.supports(capability)) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(adapter.validate(capability, params));
    }

    // ==================== 费用预估 ====================

    /**
     * 费用预估
     */
    @PostMapping("/{adapterId}/cost/{capabilityCode}")
    public ResponseEntity<CostEstimate> estimateCost(
            @PathVariable String adapterId,
            @PathVariable String capabilityCode,
            @RequestBody Map<String, Object> params) {

        ModelAdapter adapter = registry.getAdapter(adapterId);
        Capability capability = Capability.fromCode(capabilityCode);

        if (!adapter.supports(capability)) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(adapter.estimateCost(capability, params));
    }

    // ==================== 私有方法 ====================

    /**
     * 确保 requiredParams 里的参数 required=true，optionalParams 里的 required=false
     * 修复 ParamDef @Builder.Default required=true 导致 optionalParams 参数也被标记为必填的 bug
     */
    private void markRequiredFlags(CapabilitySchema schema) {
        if (schema.getRequiredParams() != null) {
            for (ParamDef p : schema.getRequiredParams()) {
                p.setRequired(true);
            }
        }
        if (schema.getOptionalParams() != null) {
            for (ParamDef p : schema.getOptionalParams()) {
                p.setRequired(false);
            }
        }
    }

    /**
     * 通过 compositeTaskId 查找 generation_task
     */
    private GenerationTask findDbTask(String compositeTaskId) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getTaskId, compositeTaskId)
                .orderByDesc(GenerationTask::getId)
                .last("LIMIT 1");
        return generationTaskService.getOne(wrapper);
    }

    /**
     * 查找已存在的 media_asset（防重复创建）
     * 通过 generation_task 的 output_url 或 source + providerTaskId 匹配
     */
    private Long findExistingAssetId(GenerationTask dbTask) {
        if (dbTask.getOutputUrl() != null) {
            var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MediaAsset>()
                    .eq(MediaAsset::getUrl, dbTask.getOutputUrl())
                    .orderByDesc(MediaAsset::getId)
                    .last("LIMIT 1");
            MediaAsset existing = mediaAssetService.getOne(wrapper);
            if (existing != null) return existing.getId();
        }
        return null;
    }

    /**
     * 构建媒体资产 metadata（仿 ComfyUI：产出物自描述）
     * 包含完整生成参数，可复现
     */
    private String buildMetadata(GenerationTask dbTask, MediaResult result, TaskHandle handle) {
        try {
            Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("adapterId", handle.getAdapterId());
            meta.put("capability", handle.getCapability() != null ? handle.getCapability().getCode() : null);
            meta.put("providerTaskId", handle.getProviderTaskId());
            if (result != null) {
                meta.put("originalUrl", result.getOriginalUrl());
                meta.put("mediaType", result.getMediaType());
                if (result.getDurationMs() != null) meta.put("durationMs", result.getDurationMs());
                if (result.getResolution() != null) meta.put("resolution", result.getResolution());
                if (result.getFileSize() != null) meta.put("fileSize", result.getFileSize());
                if (result.getMetadata() != null) meta.put("providerMeta", result.getMetadata());
            } else if (handle.getResultUrl() != null) {
                // 同步返回（如 Seedance 文生图）
                meta.put("originalUrl", handle.getResultUrl());
            }
            // 原始生成参数
            if (dbTask.getConfig() != null) meta.put("params", dbTask.getConfig());
            meta.put("generatedAt", OffsetDateTime.now().toString());
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 将 params 中的本地 URL（/api/v1/media/{id}/file）转成公网可访问的 URL
     * <p>
     * Kling 等 API 需要公网 URL，前端传的是本地路径。
     * 根据 storage_providers 配置自动转换。
     */
    private Map<String, Object> resolveLocalUrls(Map<String, Object> params) {
        if (params == null) return null;

        Map<String, Object> resolved = new java.util.HashMap<>(params);

        // 需要转换的参数名（图片/视频 URL）
        // 同时支持 Schema 定义的短名和 _url 后缀
        String[] urlKeys = {
                "image", "image_url", "image_tail", "image_tail_url",
                "first_frame", "first_frame_url", "last_frame", "last_frame_url",
                "video_url"
        };

        for (String key : urlKeys) {
            Object value = resolved.get(key);
            if (value instanceof String urlStr) {
                resolved.put(key, resolveSingleUrl(urlStr));
            }
        }

        return resolved;
    }

    /**
     * 单个 URL 转换
     * - /api/v1/media/{id}/file → 查 media_assets → 拿公网 URL
     * - 其他 URL 原样返回
     */
    private String resolveSingleUrl(String url) {
        if (url == null || url.isEmpty()) return url;

        // 匹配 /api/v1/media/{id}/file 格式
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/api/v1/media/(\\d+)/file").matcher(url);
        if (matcher.find()) {
            long assetId = Long.parseLong(matcher.group(1));
            MediaAsset asset = mediaAssetService.getById(assetId);
            if (asset != null) {
                // 优先用 storage 的公网 URL
                if (asset.getUrl() != null && !asset.getUrl().startsWith("/api/v1/") && !asset.getUrl().startsWith("upload://")) {
                    log.info("[AdapterController] Resolved local URL: {} → {}", url, asset.getUrl());
                    return asset.getUrl();
                }
                // 其次拼接 storage provider 的公网 URL
                if (asset.getStorageKey() != null && asset.getStorageProviderId() != null) {
                    String publicUrl = storageService.getPublicUrl(asset.getStorageProviderId(), asset.getStorageKey());
                    log.info("[AdapterController] Resolved via storage: {} → {}", url, publicUrl);
                    return publicUrl;
                }
                // 最后用 storage provider 的公网前缀 + localPath
                com.luciano.entity.StorageProviderConfig provider = storageProviderService.getById(asset.getStorageProviderId());
                if (provider != null && asset.getLocalPath() != null) {
                    String publicUrl = provider.getPublicUrl() + "/api/v1/media/" + asset.getId() + "/file";
                    log.warn("[AdapterController] Resolved via local provider (may not be publicly accessible): {} → {}", url, publicUrl);
                    return publicUrl;
                }
            }
            log.warn("[AdapterController] Cannot resolve local URL: {} — asset not found or no public URL", url);
        }

        return url;
    }

    // ==================== Kling 主体管理（Element） ====================

    /**
     * 创建自定义主体（异步）
     * Body: { element_name, element_description, reference_type, frontal_image, refer_images, tag_list }
     */
    @PostMapping("/kling/elements")
    public ResponseEntity<?> createKlingElement(@RequestBody Map<String, Object> body) {
        log.info("[AdapterController] createKlingElement: element_name={}", body.get("element_name"));
        try {
            var result = klingApiClient.createElement(body);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AdapterController] createKlingElement failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询主体创建任务状态
     */
    @GetMapping("/kling/elements/{taskId}")
    public ResponseEntity<?> getKlingElementTask(@PathVariable String taskId) {
        try {
            var result = klingApiClient.getElementTask(taskId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AdapterController] getKlingElementTask failed, taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询自定义主体列表（分页）
     */
    @GetMapping("/kling/elements")
    public ResponseEntity<?> listKlingElements(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "30") int pageSize) {
        try {
            var result = klingApiClient.listCustomElements(pageNum, pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AdapterController] listKlingElements failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询官方预设主体列表（分页）
     */
    @GetMapping("/kling/elements/presets")
    public ResponseEntity<?> listKlingPresetElements(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "30") int pageSize) {
        try {
            var result = klingApiClient.listPresetElements(pageNum, pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AdapterController] listKlingPresetElements failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 删除主体
     */
    @PostMapping("/kling/elements/{elementId}/delete")
    public ResponseEntity<?> deleteKlingElement(@PathVariable String elementId) {
        try {
            var result = klingApiClient.deleteElement(elementId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AdapterController] deleteKlingElement failed, elementId={}", elementId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 一键成为主体（自动生成参考图）
     * <p>
     * 接受 1 个 mediaId + 名称 + 描述，后端自动：
     * 1. 下载原图 → 调 Omni Image 生成 2 张参考图
     * 2. 轮询等待参考图生成完成
     * 3. 用原图 + 参考图调 Kling 创建主体
     * 4. 返回 task_id + 参考图 URL
     */
    // ==================== 主体创建异步任务缓存 ====================
    // jobId -> 任务状态（进程内缓存，够用）
    private static final java.util.Map<String, Map<String, Object>> elementJobCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger jobIdCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    @PostMapping("/kling/elements/auto-from-media")
    public ResponseEntity<?> autoCreateKlingElementFromMedia(@RequestBody Map<String, Object> body) {
        String elementName = (String) body.get("element_name");
        String elementDesc = (String) body.getOrDefault("element_desc", "");
        Object mediaIdObj = body.get("media_id");
        String tagId = (String) body.get("tag_id");
        if (elementName == null || elementName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "element_name 不能为空"));
        }
        if (mediaIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "media_id 不能为空"));
        }
        Long mediaId = ((Number) mediaIdObj).longValue();

        // 生成 jobId，立即返回，后台线程跑完整流程
        String jobId = "element_job_" + jobIdCounter.incrementAndGet();
        Map<String, Object> jobState = new java.util.concurrent.ConcurrentHashMap<>();
        jobState.put("status", "processing");
        jobState.put("step", "正在生成参考图...");
        jobState.put("element_name", elementName);
        jobState.put("media_id", mediaId);
        elementJobCache.put(jobId, jobState);

        // 后台线程执行
        new Thread(() -> {
            try {
                // 1. 下载原图
                MediaAsset asset = mediaAssetService.getById(mediaId);
                if (asset == null) {
                    jobState.put("status", "failed");
                    jobState.put("error", "media_id " + mediaId + " 不存在");
                    return;
                }
                byte[] imageBytes = downloadMediaAsBytes(asset);
                String mimeType = asset.getMediaType() != null ? asset.getMediaType() : "image/jpeg";
                String frontalBase64 = "data:" + mimeType + ";base64," + java.util.Base64.getEncoder().encodeToString(imageBytes);
                log.info("[autoCreateElement] jobId={} step 1: download source image, size={}", jobId, imageBytes.length);

                // 2. 水平翻转生成参考图（毫秒级，不需要调 Omni Image）
                byte[] flippedBytes = flipImageHorizontal(imageBytes, mimeType);
                String refBase64 = "data:" + mimeType + ";base64," + java.util.Base64.getEncoder().encodeToString(flippedBytes);
                log.info("[autoCreateElement] jobId={} step 2: flipped image for reference", jobId);

                // --- 备选方案：如果翻转被 Kling 拒绝，改回 Omni Image 生成参考图 ---
                // String refPrompt = "Based on this image, generate 2 reference views of the same person: one side profile view and one three-quarter view. Maintain the same person's identity, hairstyle, and clothing. Clean background.";
                // Map<String, Object> omniBody = new java.util.LinkedHashMap<>();
                // omniBody.put("model_name", "kling-image-o1");
                // omniBody.put("prompt", refPrompt);
                // omniBody.put("n", 2);
                // omniBody.put("resolution", "1k");
                // omniBody.put("aspect_ratio", "1:1");
                // java.util.List<Map<String, Object>> imageList = new java.util.ArrayList<>();
                // imageList.add(Map.of("image", frontalBase64));
                // omniBody.put("image_list", imageList);
                // JsonNode omniResponse = klingApiClient.post(KlingConstants.PATH_OMNI_IMAGE, omniBody, JsonNode.class);
                // ... 轮询等 Omni Image 完成 ...
                // --- 备选方案结束 ---

                // 3. 调 Kling 创建主体
                jobState.put("step", "正在创建主体...");
                String finalDesc = (elementDesc == null || elementDesc.isBlank()) ? elementName : elementDesc;
                java.util.List<Map<String, Object>> referImages = new java.util.ArrayList<>();
                referImages.add(Map.of("image_url", refBase64));

                Map<String, Object> elementImageList = new java.util.HashMap<>();
                elementImageList.put("frontal_image", frontalBase64);
                elementImageList.put("refer_images", referImages);

                Map<String, Object> klingBody = new java.util.HashMap<>();
                klingBody.put("element_name", elementName);
                klingBody.put("element_description", finalDesc);
                klingBody.put("reference_type", "image_refer");
                klingBody.put("element_image_list", elementImageList);

                // tag_list
                if (tagId != null && !tagId.isBlank()) {
                    java.util.List<Map<String, Object>> tagList = new java.util.ArrayList<>();
                    tagList.add(Map.of("tag_id", tagId));
                    klingBody.put("tag_list", tagList);
                }

                log.info("[autoCreateElement] jobId={} step 3: create Kling element", jobId);
                var result = klingApiClient.createElement(klingBody);

                // 提取 task_id
                String klingTaskId = null;
                if (result.has("data") && result.get("data").has("task_id")) {
                    klingTaskId = result.get("data").get("task_id").asText();
                }

                jobState.put("status", "kling_processing");
                jobState.put("step", "Kling 处理中...");
                jobState.put("kling_task_id", klingTaskId);
                jobState.put("kling_result", result);
                jobState.put("frontal_media_id", mediaId);

            } catch (Exception e) {
                log.error("[autoCreateElement] jobId={} failed", jobId, e);
                jobState.put("status", "failed");
                jobState.put("error", e.getMessage());
            }
        }, "element-create-" + jobId).start();

        return ResponseEntity.ok(Map.of("job_id", jobId, "status", "processing"));
    }

    /** 查询主体创建异步任务状态 */
    @GetMapping("/kling/elements/auto-from-media/{jobId}")
    public ResponseEntity<?> getAutoElementJobStatus(@PathVariable String jobId) {
        Map<String, Object> state = elementJobCache.get(jobId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    /** 清理已完成的任务缓存 */
    @org.springframework.web.bind.annotation.DeleteMapping("/kling/elements/auto-from-media/{jobId}")
    public ResponseEntity<?> deleteAutoElementJob(@PathVariable String jobId) {
        elementJobCache.remove(jobId);
        return ResponseEntity.ok().build();
    }

    /**
     * 水平翻转图片（生成参考图用）
     */
    private byte[] flipImageHorizontal(byte[] imageBytes, String mimeType) throws Exception {
        java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        int w = original.getWidth();
        int h = original.getHeight();
        java.awt.image.BufferedImage flipped = new java.awt.image.BufferedImage(w, h, original.getType());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                flipped.setRGB(x, y, original.getRGB(w - 1 - x, y));
            }
        }
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        String format = mimeType != null && mimeType.contains("png") ? "png" : "jpg";
        javax.imageio.ImageIO.write(flipped, format, baos);
        return baos.toByteArray();
    }

    /** 从 Omni Image 响应中提取 task_id */
    private String extractOmniTaskId(JsonNode response) {
        if (response.has("data") && response.get("data").has("task_id")) {
            return response.get("data").get("task_id").asText();
        }
        return null;
    }

    /** 从 Omni Image 轮询响应中提取状态 */
    private String extractOmniStatus(JsonNode response) {
        if (response.has("data") && response.get("data").has("task_status")) {
            return response.get("data").get("task_status").asText();
        }
        return "unknown";
    }

    /** 从 Omni Image 完成响应中提取图片 URL */
    private java.util.List<String> extractOmniImageUrls(JsonNode response) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        if (!response.has("data")) return urls;
        JsonNode data = response.get("data");
        if (!data.has("task_result")) return urls;
        JsonNode result = data.get("task_result");
        if (result.has("images") && result.get("images").isArray()) {
            for (JsonNode img : result.get("images")) {
                if (img.has("url")) urls.add(img.get("url").asText());
            }
        }
        return urls;
    }

    /** 下载 URL 为字节数组（用于下载 Kling 生成的参考图） */
    private byte[] downloadUrlAsBytes(String url) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .build();
        java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    }

    /**
     * 从画布图片创建主体（方案C）
     * <p>
     * 接受 mediaId 列表，后端下载图片转 base64，调 Kling 创建主体。
     * 第一个图片作为正面图，其余作为参考图。
     */
    @PostMapping("/kling/elements/from-media")
    public ResponseEntity<?> createKlingElementFromMedia(@RequestBody Map<String, Object> body) {
        String elementName = (String) body.get("element_name");
        String elementDesc = (String) body.getOrDefault("element_desc", "");
        Object mediaIdsObj = body.get("media_ids");
        String tagId = (String) body.get("tag_id");
        if (elementName == null || elementName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "element_name 不能为空"));
        }
        if (mediaIdsObj == null || !(mediaIdsObj instanceof java.util.List<?> mediaIdList) || mediaIdList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "media_ids 不能为空"));
        }
        try {
            // 下载图片并转 base64
            java.util.List<String> base64Images = new java.util.ArrayList<>();
            for (Object idObj : mediaIdList) {
                Long mediaId = ((Number) idObj).longValue();
                MediaAsset asset = mediaAssetService.getById(mediaId);
                if (asset == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "media_id " + mediaId + " 不存在"));
                }
                // 下载图片到本地
                byte[] imageBytes = downloadMediaAsBytes(asset);
                String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
                String mimeType = asset.getMediaType() != null ? asset.getMediaType() : "image/jpeg";
                String dataUrl = "data:" + mimeType + ";base64," + base64;
                base64Images.add(dataUrl);
            }

            // 构建 Kling API 请求（snake_case + 嵌套结构）
            String finalDesc = (elementDesc == null || elementDesc.isBlank()) ? elementName : elementDesc;
            log.info("[AdapterController] createKlingElementFromMedia: elementName='{}', image_count={}, finalDesc='{}'",
                    elementName, base64Images.size(), finalDesc);

            // media_ids[0] = 正面图，media_ids[1..N] = 参考图
            // Kling 要求 refer_images 1-3 张，前端已做校验
            java.util.List<Map<String, Object>> referImages = new java.util.ArrayList<>();
            for (int i = 1; i < base64Images.size(); i++) {
                referImages.add(Map.of("image_url", base64Images.get(i)));
            }
            if (referImages.size() > 3) {
                referImages = referImages.subList(0, 3);
            }
            if (referImages.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "参考图不能为空，Kling要求 1-3 张参考图"));
            }

            // element_image_list 嵌套对象
            Map<String, Object> elementImageList = new java.util.HashMap<>();
            elementImageList.put("frontal_image", base64Images.get(0));  // 第一张作为正面图
            elementImageList.put("refer_images", referImages);

            Map<String, Object> klingBody = new java.util.HashMap<>();
            klingBody.put("element_name", elementName);
            klingBody.put("element_description", finalDesc);
            klingBody.put("reference_type", "image_refer");
            klingBody.put("element_image_list", elementImageList);

            // tag_list
            if (tagId != null && !tagId.isBlank()) {
                java.util.List<Map<String, Object>> tagList = new java.util.ArrayList<>();
                tagList.add(Map.of("tag_id", tagId));
                klingBody.put("tag_list", tagList);
            }

            log.info("[AdapterController] createKlingElementFromMedia: element_name={}, image_count={}, finalDesc={}",
                    elementName, base64Images.size(), finalDesc);
            var result = klingApiClient.createElement(klingBody);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AdapterController] createKlingElementFromMedia failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 下载 media asset 为字节数组
     */
    private byte[] downloadMediaAsBytes(MediaAsset asset) throws Exception {
        // 优先从本地文件读取
        if (asset.getLocalPath() != null && !asset.getLocalPath().isBlank()) {
            java.io.File localFile = new java.io.File(asset.getLocalPath());
            if (localFile.exists()) {
                return java.nio.file.Files.readAllBytes(localFile.toPath());
            }
        }
        // 从对象存储下载
        if (asset.getStorageProviderId() != null && asset.getStorageKey() != null && !asset.getStorageKey().isBlank()) {
            var resource = storageService.downloadAsResource(asset.getStorageProviderId(), asset.getStorageKey(), null);
            if (resource != null) {
                try (var is = resource.getInputStream()) {
                    return is.readAllBytes();
                }
            }
        }
        // 从原始 URL 下载（用 Java HttpClient）
        if (asset.getUrl() != null && !asset.getUrl().isBlank()) {
            String dlUrl = asset.getUrl();
            if (dlUrl.startsWith("/")) {
                dlUrl = "http://localhost:8090" + dlUrl;
            }
            java.net.URI uri = java.net.URI.create(dlUrl);
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            throw new RuntimeException("下载失败: HTTP " + resp.statusCode());
        }
        throw new IllegalStateException("MediaAsset " + asset.getId() + " 无可用下载源");
    }
}
