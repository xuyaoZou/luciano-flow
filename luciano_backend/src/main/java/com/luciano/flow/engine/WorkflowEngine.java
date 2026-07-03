package com.luciano.flow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luciano.adapter.*;
import com.luciano.flow.*;
import com.luciano.flow.entity.WorkflowExecution;
import com.luciano.flow.repository.WorkflowExecutionMapper;
import com.luciano.flow.repository.WorkflowMapper;
import com.luciano.flow.entity.Workflow;
import com.luciano.service.MediaAssetService;
import com.luciano.service.MediaDownloadService;
import com.luciano.entity.MediaAsset;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.ApiOperationLog;
import com.luciano.entity.User;
import com.luciano.service.ApiOperationLogService;
import com.luciano.repository.mapper.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎
 * <p>
 * 核心流程：
 * 1. 解析 DAG，拓扑排序，分层
 * 2. 逐层执行：同层节点并行，层间串行
 * 3. 上游输出 → 映射到下游输入（连线数据传递）
 * 4. Capability 节点走适配器，特殊节点走 SpecialNodeExecutor
 * 5. 执行结果写入 WorkflowExecution.nodeResults
 * 6. 每个关键步骤通过 SSE 推送事件到前端
 * <p>
 * 参考设计文档 §6 执行引擎 + §8.5 状态反馈。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowMapper workflowMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final AdapterRegistry adapterRegistry;
    private final WorkflowSseService sseService;
    private final MediaAssetService mediaAssetService;
    private final MediaDownloadService mediaDownloadService;
    private final ApiOperationLogService operationLogService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    private static final long NODE_TIMEOUT_MS = 10 * 60 * 1000; // 单节点最大超时 10 分钟
    private static final long POLL_INTERVAL_MS = 3000; // 轮询间隔 3 秒

    /**
     * 异步执行工作流
     *
     * @param workflowId 工作流 ID
     * @param userId     执行用户 ID
     * @return 执行实例 ID
     */
    public Long executeAsync(Long workflowId, Long userId) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        // 创建执行实例
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setUserId(userId);
        execution.setProjectId(workflow.getProjectId());
        execution.setStatus("running");
        execution.setDagSnapshot(workflow.getNodes()); // 快照：冻结节点
        execution.setNodeResults("{}");
        execution.setStartedAt(OffsetDateTime.now());
        executionMapper.insert(execution);

        // 异步执行（传递用户上下文）
        final Long contextUserId = userId;
        CompletableFuture.runAsync(() -> {
            try {
                // 在异步线程中设置用户上下文，供操作日志使用
                if (contextUserId != null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(contextUserId, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                executeDag(execution.getId(), workflow);
            } catch (Exception e) {
                log.error("工作流执行失败, executionId={}", execution.getId(), e);
                markFailed(execution.getId(), e.getMessage());
                sseService.sendExecutionFailed(execution.getId(), e.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return execution.getId();
    }

    /**
     * 异步执行单个节点
     * <p>
     * 只执行指定节点。如果该节点有上游输入，从最近一次成功的执行结果中获取输出。
     * 适用于开发调试场景：修改参数后只重跑一个节点，不需要重新执行整个工作流。
     *
     * @param workflowId 工作流 ID
     * @param userId     执行用户 ID
     * @param nodeId      要执行的节点 ID
     * @return 执行实例 ID
     */
    public Long executeNodeAsync(Long workflowId, Long userId, String nodeId) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        // 创建执行实例（标记为单节点执行）
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setUserId(userId);
        execution.setProjectId(workflow.getProjectId());
        execution.setStatus("running");
        execution.setDagSnapshot(workflow.getNodes()); // 快照
        execution.setNodeResults("{}");
        execution.setStartedAt(OffsetDateTime.now());
        executionMapper.insert(execution);

        final Long contextUserId = userId;
        CompletableFuture.runAsync(() -> {
            try {
                if (contextUserId != null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(contextUserId, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                executeSingleNode(execution.getId(), workflow, nodeId);
            } catch (Exception e) {
                log.error("单节点执行失败, executionId={}, nodeId={}", execution.getId(), nodeId, e);
                markFailed(execution.getId(), e.getMessage());
                sseService.sendExecutionFailed(execution.getId(), e.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return execution.getId();
    }

    /**
     * 执行 DAG（核心逻辑）
     */
    private void executeDag(Long executionId, Workflow workflow) {
        WorkflowExecution execution = executionMapper.selectById(executionId);
        String nodesJson = execution.getDagSnapshot();
        String edgesJson = workflow.getEdges();

        List<WorkflowNode> nodes;
        List<WorkflowEdge> edges;
        try {
            nodes = objectMapper.readValue(nodesJson, new TypeReference<>() {});
            edges = objectMapper.readValue(edgesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析工作流 JSON 失败", e);
        }

        // 1. DAG 解析 + 拓扑排序
        List<List<String>> layers = DagResolver.resolve(nodes, edges);
        log.info("工作流执行开始, executionId={}, 共{}层, {}个节点", executionId, layers.size(), nodes.size());

        // 推送：执行开始
        sseService.sendExecutionStarted(executionId, workflow.getId(), nodes.size());

        // 构建节点 Map
        Map<String, WorkflowNode> nodeMap = nodes.stream()
                .collect(Collectors.toMap(WorkflowNode::getId, n -> n));

        // 节点输出存储
        Map<String, Map<String, OutputRef>> allOutputs = new ConcurrentHashMap<>();

        // 节点执行结果
        Map<String, Map<String, Object>> nodeResults = new ConcurrentHashMap<>();

        // 2. 逐层执行
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<String> layer = layers.get(layerIndex);

            // 推送：层开始
            sseService.sendLayerStarted(executionId, layerIndex, layer);

            // 同层节点并行执行
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String nodeId : layer) {
                WorkflowNode node = nodeMap.get(nodeId);
                // 传递 SecurityContext 和 LogContext 到异步线程
                var parentAuth = SecurityContextHolder.getContext().getAuthentication();
                var parentLogCtx = ModelHttpClient.LogContext.current();
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        // 恢复 SecurityContext
                        if (parentAuth != null) {
                            SecurityContextHolder.getContext().setAuthentication(parentAuth);
                        }
                        // 恢复 LogContext
                        if (parentLogCtx != null) {
                            ModelHttpClient.LogContext.set(parentLogCtx);
                        }

                        // 推送：节点开始
                        sseService.sendNodeStarted(executionId, nodeId, node.getType());

                        Map<String, OutputRef> outputs = executeNode(executionId, node, edges, allOutputs, nodes);
                        allOutputs.put(nodeId, outputs);

                        // 记录结果
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "completed");
                        if (!outputs.isEmpty()) {
                            OutputRef firstOutput = outputs.values().iterator().next();
                            result.put("outputUrl", firstOutput.getUrl());
                            result.put("assetId", firstOutput.getAssetId());
                            result.put("dataType", firstOutput.getDataType() != null
                                    ? firstOutput.getDataType().getCode() : null);
                        }
                        nodeResults.put(nodeId, result);

                        // 推送：节点完成（含多结果）
                        OutputRef firstOutput = outputs.isEmpty() ? null : outputs.values().iterator().next();
                        String nodeOutputUrl = firstOutput != null ? firstOutput.getUrl() : null;
                        Long nodeAssetId = firstOutput != null ? firstOutput.getAssetId() : null;
                        // 优先传 media proxy URL，前端可直接使用
                        String displayUrl = nodeAssetId != null
                                ? "/api/v1/media/" + nodeAssetId + "/file"
                                : nodeOutputUrl;
                        // 构建多结果 display URL 列表
                        List<String> displayUrls = null;
                        List<Long> displayAssetIds = null;
                        if (firstOutput != null && firstOutput.getAllUrls() != null && firstOutput.getAllUrls().size() > 1) {
                            displayUrls = new ArrayList<>();
                            displayAssetIds = new ArrayList<>();
                            for (int i = 0; i < firstOutput.getAllUrls().size(); i++) {
                                String url = firstOutput.getAllUrls().get(i);
                                Long aid = i < firstOutput.getAllAssetIds().size() ? firstOutput.getAllAssetIds().get(i) : null;
                                String du = aid != null ? "/api/v1/media/" + aid + "/file" : url;
                                displayUrls.add(du);
                                displayAssetIds.add(aid);
                            }
                        }
                        sseService.sendNodeCompleted(executionId, nodeId, node.getType(),
                                displayUrl, nodeAssetId, displayUrls, displayAssetIds);

                    } catch (Exception e) {
                        log.error("节点执行失败, nodeId={}", nodeId, e);
                        String friendlyMsg = friendlyErrorMessage(e.getMessage());
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "failed");
                        result.put("error", friendlyMsg);
                        nodeResults.put(nodeId, result);

                        // 推送：节点失败
                        sseService.sendNodeFailed(executionId, nodeId, friendlyMsg);

                        throw new RuntimeException("节点 " + nodeId + " 执行失败: " + friendlyMsg, e);
                    } finally {
                        SecurityContextHolder.clearContext();
                        ModelHttpClient.LogContext.clear();
                    }
                }));
            }

            // 等待当前层所有节点完成
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(NODE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException("第 " + (layerIndex + 1) + " 层执行超时");
            } catch (ExecutionException e) {
                throw new RuntimeException("第 " + (layerIndex + 1) + " 层执行失败: " + e.getCause().getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("执行被中断");
            }

            // 层完成后，推送数据流事件（连线传递）
            for (String nodeId : layer) {
                for (WorkflowEdge edge : edges) {
                    if (edge.getSourceNodeId().equals(nodeId)) {
                        Map<String, OutputRef> sourceOutputs = allOutputs.get(nodeId);
                        if (sourceOutputs != null && sourceOutputs.containsKey(edge.getSourceSlot())) {
                            sseService.sendDataFlow(executionId,
                                    nodeId, edge.getSourceSlot(),
                                    edge.getTargetNodeId(), edge.getTargetSlot(),
                                    edge.getDataType());
                        }
                    }
                }
            }

            // 推送：层完成
            sseService.sendLayerCompleted(executionId, layerIndex);
        }

        // 3. 更新执行实例状态
        updateNodeResults(executionId, nodeResults);
        markCompleted(executionId);

        // 推送：执行完成
        sseService.sendExecutionCompleted(executionId);
        log.info("工作流执行完成, executionId={}", executionId);
    }

    /**
     * 执行单个节点
     */
    private Map<String, OutputRef> executeNode(
            Long executionId,
            WorkflowNode node,
            List<WorkflowEdge> edges,
            Map<String, Map<String, OutputRef>> allOutputs,
            List<WorkflowNode> nodes) {

        String nodeType = node.getType();

        // 引用节点：直接返回引用的 URL，不走适配器
        if ("reference".equals(nodeType)) {
            return executeReferenceNode(node);
        }

        // 特殊节点：直接执行
        if (NodeType.isSpecialNode(nodeType)) {
            return SpecialNodeExecutor.execute(nodeType, node.getParams());
        }

        // Capability 节点：走适配器
        Capability capability = Capability.fromCode(nodeType);
        AdapterNodeExecutor executor = new AdapterNodeExecutor(adapterRegistry);

        // 设置 LogContext，让 ModelHttpClient 拦截器自动记录完整的 HTTP 操作日志
        // （method/path/requestBody/responseBody 由 ModelHttpClient 自动填充）
        Long currentUserId = getUserId();
        String adapterId = node.getAdapterId() != null ? node.getAdapterId() : adapterRegistry.route(capability, null).getId();
        Long projectId = getProjectId(executionId);
        ModelHttpClient.LogContext logCtx = new ModelHttpClient.LogContext()
                .userId(currentUserId)
                .username(getUsername(currentUserId))
                .adapterId(adapterId)
                .capability(capability.getCode())
                .operationType("submit")
                .projectId(projectId);
        ModelHttpClient.LogContext.set(logCtx);

        try {
            // 1. 解析上游输入，合并参数
            Map<String, Object> resolvedParams = resolveNodeParams(node, edges, allOutputs, nodes);
            log.info("节点 {} ({}) 解析后参数: {}", node.getId(), nodeType, resolvedParams.keySet());
            log.debug("节点 {} ({}) 解析后参数详情: {}", node.getId(), nodeType, resolvedParams);

            // 2. 校验
            // 兼容修复：multi 端口连线注入的是 image_list，但旧 SeedanceAdapter class 只认 image/image_url
            // 在校验前把 image_list 第一张展开为 image（如果 image 不存在的话）
            if (capability == Capability.IMAGE_TO_VIDEO && !resolvedParams.containsKey("image") && !resolvedParams.containsKey("image_url")) {
                Object imageList = resolvedParams.get("image_list");
                if (imageList instanceof List<?> list && !list.isEmpty()) {
                    resolvedParams.put("image", list.get(0));
                    log.info("节点 {} 兼容修复: image_list[0] -> image = {}", node.getId(), list.get(0));
                }
            }
            // 同理: first_frame_list -> first_frame
            if (capability == Capability.FIRST_LAST_FRAME && !resolvedParams.containsKey("first_frame") && !resolvedParams.containsKey("first_frame_url")) {
                Object ffList = resolvedParams.get("first_frame_list");
                if (ffList instanceof List<?> list && !list.isEmpty()) {
                    resolvedParams.put("first_frame", list.get(0));
                    log.info("节点 {} 兼容修复: first_frame_list[0] -> first_frame = {}", node.getId(), list.get(0));
                }
            }
            ValidationResult validation = executor.validate(capability, resolvedParams);
            if (!validation.isValid()) {
                log.warn("节点 {} 参数校验失败, errors={}, params={}", node.getId(), validation.getErrors(), resolvedParams);
                throw new IllegalArgumentException("参数校验失败: " + validation.getErrors());
            }

            // 3. 提交任务
            long startTime = System.currentTimeMillis();
            TaskHandle handle = executor.submit(capability, resolvedParams, node.getAdapterId());
            log.info("任务已提交, nodeId={}, capability={}, taskId={}", node.getId(), capability, handle.getTaskId());

            // 更新 LogContext：补充 taskId/providerTaskId
            logCtx.taskId(handle.getTaskId()).providerTaskId(handle.getProviderTaskId());

            // 4. 轮询直到完成
            TaskStatus status;
            while (true) {
                logCtx.operationType("poll");
                status = executor.poll(handle, node.getAdapterId());

                if (status == TaskStatus.COMPLETED) {
                    break;
                }
                if (status == TaskStatus.FAILED) {
                    throw new RuntimeException("任务失败");
                }

                // 推送：轮询进度
                sseService.sendNodeProgress(executionId, node.getId(), status.name());

                if (System.currentTimeMillis() - startTime > NODE_TIMEOUT_MS) {
                    throw new RuntimeException("节点执行超时: " + node.getId());
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("执行被中断");
                }
            }

            // 5. 下载结果
            logCtx.operationType("download");
            MediaResult mediaResult = executor.download(handle, node.getAdapterId());

            // 获取输出端口类型
            PortType outputType = AdapterNodeExecutor.getOutputPortType(capability);

            // 6. 为每个结果创建 media_asset 记录 + 下载到本地
            String mediaType = outputType == PortType.IMAGE ? "image" : "video";
            String mediaUrl = mediaResult.getOriginalUrl();
            Long assetId = null;
            List<String> allUrls = new ArrayList<>();
            List<Long> allAssetIds = new ArrayList<>();

            // 主结果
            try {
                MediaAsset asset = mediaAssetService.addAsset(
                        projectId, currentUserId, null,
                        "flow:" + capability.getCode(), mediaType, mediaUrl,
                        null, null, handle.getTaskId(), null
                );
                try {
                    mediaDownloadService.downloadToLocal(asset);
                    assetId = asset.getId();
                    log.info("[WorkflowEngine] Asset created + downloaded: assetId={}, localPath={}", asset.getId(), asset.getLocalPath());
                } catch (Exception dlEx) {
                    assetId = asset.getId();
                    log.warn("[WorkflowEngine] Local download failed for assetId={}, will use remote URL", asset.getId(), dlEx.getMessage());
                }
            } catch (Exception e) {
                log.warn("[WorkflowEngine] Failed to create media_asset for nodeId={}: {}", node.getId(), e.getMessage());
            }
            allUrls.add(mediaUrl);
            allAssetIds.add(assetId);

            // 额外结果
            for (String extraUrl : mediaResult.getAdditionalUrls()) {
                Long extraAssetId = null;
                try {
                    MediaAsset extraAsset = mediaAssetService.addAsset(
                            projectId, currentUserId, null,
                            "flow:" + capability.getCode(), mediaType, extraUrl,
                            null, null, handle.getTaskId(), null
                    );
                    try {
                        mediaDownloadService.downloadToLocal(extraAsset);
                        extraAssetId = extraAsset.getId();
                    } catch (Exception dlEx) {
                        extraAssetId = extraAsset.getId();
                        log.warn("[WorkflowEngine] Local download failed for extra assetId={}", extraAsset.getId());
                    }
                } catch (Exception e) {
                    log.warn("[WorkflowEngine] Failed to create extra media_asset: {}", e.getMessage());
                }
                allUrls.add(extraUrl);
                allAssetIds.add(extraAssetId);
            }

            // 7. 写完成日志（业务层完成，不依赖 HTTP 调用）
            long durationMs = System.currentTimeMillis() - startTime;
            logComplete(currentUserId, adapterId, capability, handle, projectId, durationMs, mediaUrl);

            // 8. 构建输出
            Map<String, OutputRef> outputs = new HashMap<>();
            String outputSlotName = CapabilityPorts.getOutputPorts(capability).get(0).getName();

            OutputRef outputRef = OutputRef.builder()
                    .url(mediaUrl)
                    .localUrl(mediaResult.getLocalPath())
                    .assetId(assetId)
                    .dataType(outputType)
                    .allUrls(allUrls)
                    .allAssetIds(allAssetIds)
                    .build();
            outputs.put(outputSlotName, outputRef);

            return outputs;
        } finally {
            ModelHttpClient.LogContext.clear();
        }
    }

    /**
     * 解析节点参数：连线传入的值覆盖手动参数
     */
    /**
     * 解析节点参数：连线值优先于手动填写值
     * <p>
     * 1. 复制节点手动参数
     * 2. 遍历所有连入边，从上游输出取值注入
     * 3. multi=true 的端口收集成数组，multi=false 的端口后连覆盖前值
     * 4. 执行端口类型转换（IMAGE→REFERENCE 等）
     */
    private Map<String, Object> resolveNodeParams(
            WorkflowNode node,
            List<WorkflowEdge> edges,
            Map<String, Map<String, OutputRef>> allOutputs,
            List<WorkflowNode> nodes) {

        Map<String, Object> params = new HashMap<>();
        if (node.getParams() != null) {
            params.putAll(node.getParams());
        }

        // 获取当前节点的输入端口定义，用于判断 multi 和类型转换
        Map<String, PortDef> inputPortMap = getPortDefMap(node);

        // 查找所有连入当前节点的边
        List<WorkflowEdge> incomingEdges = edges.stream()
                .filter(e -> e.getTargetNodeId().equals(node.getId()))
                .toList();

        log.debug("[resolveNodeParams] nodeId={}, incomingEdges={}, allOutputs keys={}",
                node.getId(), incomingEdges.size(), allOutputs.keySet());

        // multi=true 的端口需要收集成数组
        Map<String, List<Object>> multiValues = new HashMap<>();

        for (WorkflowEdge edge : incomingEdges) {
            Map<String, OutputRef> sourceOutputs = allOutputs.get(edge.getSourceNodeId());
            if (sourceOutputs == null) {
                log.debug("[resolveNodeParams] edge source {} has no outputs", edge.getSourceNodeId());
                continue;
            }

            OutputRef sourceRef = sourceOutputs.get(edge.getSourceSlot());
            if (sourceRef == null) {
                log.debug("[resolveNodeParams] sourceSlot {} not found in outputs {}", edge.getSourceSlot(), sourceOutputs.keySet());
                continue;
            }

            Object value = resolveOutputValue(sourceRef, edge, inputPortMap.get(edge.getTargetSlot()));
            log.debug("[resolveNodeParams] edge {}#{} -> {}#{}, value={}", edge.getSourceNodeId(), edge.getSourceSlot(), edge.getTargetSlot(), edge.getTargetSlot(), value);

            PortDef targetPort = inputPortMap.get(edge.getTargetSlot());
            if (targetPort != null && targetPort.isMulti()) {
                // multi=true: 收集成数组
                multiValues.computeIfAbsent(edge.getTargetSlot(), k -> new ArrayList<>()).add(value);
            } else {
                // multi=false: 连线值覆盖手动参数
                // 如果上游有多结果，用 selectedIndex 选择特定索引的那张
                Object finalValue = value;
                if (sourceRef.getAllUrls() != null && sourceRef.getAllUrls().size() > 1) {
                    // 查找 source node 的 selectedIndex
                    String sourceNodeId = edge.getSourceNodeId();
                    WorkflowNode sourceNode = nodes.stream()
                            .filter(n -> n.getId().equals(sourceNodeId))
                            .findFirst().orElse(null);
                    if (sourceNode != null && sourceNode.getParams() != null) {
                        Integer selectedIndex = (Integer) sourceNode.getParams().get("selectedIndex");
                        if (selectedIndex != null && selectedIndex > 0 && selectedIndex < sourceRef.getAllUrls().size()) {
                            String selectedUrl = sourceRef.getAllUrls().get(selectedIndex);
                            // 重新解析选中索引的 URL（需要类型转换）
                            finalValue = resolveOutputValue(
                                    OutputRef.builder()
                                            .url(selectedUrl)
                                            .dataType(sourceRef.getDataType())
                                            .allUrls(List.of(selectedUrl))
                                            .build(),
                                    edge, targetPort);
                            log.debug("[resolveNodeParams] selectedIndex={} -> using url {}", selectedIndex, selectedUrl);
                        }
                    }
                }
                params.put(edge.getTargetSlot(), finalValue);
            }
        }

        // 合并 multi 值
        // multi=true 的端口，参数名加 "_list" 后缀（如 image → image_list），匹配适配器 API 参数名
        for (Map.Entry<String, List<Object>> entry : multiValues.entrySet()) {
            String portName = entry.getKey();
            String paramName = portName + "_list";

            // 如果 params 中已有用户勾选的 URL 数组（从参数面板选择），优先使用用户选择
            Object userSelection = params.get(paramName);
            if (userSelection instanceof List && !((List<?>) userSelection).isEmpty()) {
                List<?> selectedUrls = (List<?>) userSelection;
                // 检查是否是 URL 字符串数组（用户选择）而非连线收集的值
                boolean isUserSelection = selectedUrls.stream().allMatch(u -> u instanceof String)
                        && !selectedUrls.equals(entry.getValue());
                if (isUserSelection) {
                    log.debug("[resolveNodeParams] multi port {} using user selection: {}", paramName, selectedUrls);
                    continue; // 跳过连线收集，用用户选择的值
                }
            }

            // 如果手动参数中已有 _list 值，合并到数组前面
            List<Object> merged = new ArrayList<>();
            Object manualValue = params.get(paramName);
            if (manualValue != null) {
                if (manualValue instanceof List) {
                    merged.addAll((List<?>) manualValue);
                } else {
                    merged.add(manualValue);
                }
                params.remove(paramName);
            }
            // 也检查不加后缀的手动参数
            Object manualValueShort = params.get(portName);
            if (manualValueShort != null && !portName.equals(paramName)) {
                if (manualValueShort instanceof List) {
                    merged.addAll((List<?>) manualValueShort);
                } else {
                    merged.add(manualValueShort);
                }
                params.remove(portName);
            }

            merged.addAll(entry.getValue());
            params.put(paramName, merged);
        }

        // 过滤掉持久化字段，不传给适配器
        params.remove("status");
        params.remove("outputUrl");
        params.remove("outputUrls");
        params.remove("selectedIndex");
        params.remove("parentNodeId");
        params.remove("imageIndex");
        params.remove("parentUrls");
        params.remove("parentType");

        // 转换各种 list 参数中的本地代理 URL 为原始 URL
        convertProxyUrlsInList(params, "image_list");
        convertProxyUrlsInList(params, "element_list");
        convertProxyUrlsInList(params, "reference_images");
        convertProxyUrlsInList(params, "reference_images_list");
        convertProxyUrlsInList(params, "reference_videos");
        convertProxyUrlsInList(params, "reference_videos_list");
        convertProxyUrlsInList(params, "reference_audios");
        convertProxyUrlsInList(params, "reference_audios_list");
        // 单个 URL 参数也可能存了代理路径
        convertProxyUrlField(params, "image");
        convertProxyUrlField(params, "first_frame");
        convertProxyUrlField(params, "last_frame");
        convertProxyUrlField(params, "video");
        convertProxyUrlField(params, "audio");
        // element_list 特殊处理：如果值是 URL（旧数据），从节点列表查找真实 elementId
        fixElementListIds(params, nodes);

        log.debug("[resolveNodeParams] nodeId={}, resolved params keys={}, image param={}",
                node.getId(), params.keySet(), params.get("image"));
        return params;
    }

    /**
     * 修复 element_list 中的 URL 值 — 从节点列表查找对应 ElementSource 的 elementId
     */
    private void fixElementListIds(Map<String, Object> params, List<WorkflowNode> nodes) {
        Object value = params.get("element_list");
        if (!(value instanceof List)) return;
        List<?> list = (List<?>) value;
        // 收集所有 ElementSource 节点的 elementId → thumbUrl 映射
        Map<String, String> thumbToId = new HashMap<>();
        for (WorkflowNode n : nodes) {
            if ("ElementSource".equals(n.getType()) && n.getParams() != null) {
                Object elId = n.getParams().get("elementId");
                Object elThumb = n.getParams().get("elementThumb");
                if (elId != null && elThumb != null) {
                    thumbToId.put(elThumb.toString(), elId.toString());
                }
            }
        }
        if (thumbToId.isEmpty()) return;

        List<Object> fixed = new ArrayList<>();
        boolean changed = false;
        for (Object item : list) {
            if (item instanceof String s && (s.startsWith("http") || s.startsWith("/api/"))) {
                // URL 值 → 查找对应的 elementId
                String elId = thumbToId.get(s);
                if (elId != null) {
                    fixed.add(elId);
                    changed = true;
                } else {
                    fixed.add(item);
                }
            } else {
                fixed.add(item);
            }
        }
        if (changed) {
            params.put("element_list", fixed);
            log.info("[fixElementListIds] 修复 element_list 中的 URL → elementId: {}", fixed);
        }
    }

    /**
     * 转换 params 中指定 list 参数里的本地代理 URL（/api/v1/media/xxx/file）为原始 URL
     */
    private void convertProxyUrlsInList(Map<String, Object> params, String paramName) {
        Object value = params.get(paramName);
        if (!(value instanceof List)) return;
        List<?> list = (List<?>) value;
        List<Object> converted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : list) {
            String urlKey = null;
            if (item instanceof String s) urlKey = s;
            else if (item instanceof Map<?, ?> m && m.get("url") instanceof String s) urlKey = s;
            
            String resolved = null;
            if (item instanceof String s) {
                resolved = resolveProxyUrl(s);
                converted.add(resolved);
            } else if (item instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) m;
                for (String key : new String[]{"url", "image", "element_id"}) {
                    Object v = map.get(key);
                    if (v instanceof String vs && vs.startsWith("/api/v1/media/")) {
                        map.put(key, resolveProxyUrl(vs));
                    }
                }
                converted.add(map);
                if (map.get("url") instanceof String u) resolved = u;
            } else {
                converted.add(item);
            }
            
            // 去重：已见过的 URL 跳过
            if (resolved != null && !seen.add(resolved)) {
                converted.remove(converted.size() - 1);
            }
        }
        params.put(paramName, converted);
    }

    /**
     * 将本地代理 URL 转换为原始 URL
     */
    private String resolveProxyUrl(String url) {
        if (url == null || !url.startsWith("/api/v1/media/") || !url.endsWith("/file")) {
            return url;
        }
        try {
            String idStr = url.substring("/api/v1/media/".length(), url.length() - "/file".length());
            Long assetId = Long.parseLong(idStr);
            MediaAsset asset = mediaAssetService.getById(assetId);
            if (asset != null && asset.getUrl() != null) {
                return asset.getUrl();
            }
        } catch (Exception e) {
            log.warn("[resolveProxyUrl] 转换代理 URL 失败: {}", url);
        }
        return url;
    }

    /**
     * 转换单个字符串参数中的本地代理 URL 为原始 URL
     */
    private void convertProxyUrlField(Map<String, Object> params, String paramName) {
        Object value = params.get(paramName);
        if (value instanceof String s && s.startsWith("/api/v1/media/")) {
            params.put(paramName, resolveProxyUrl(s));
        }
    }

    /**
     * 解析上游输出值，执行类型转换
     * <p>
     * - PROMPT/NEGATIVE_PROMPT/TEXT 类型直接取 URL 内容（文本型）
     * - 其他类型优先用 localUrl（代理地址），fallback 到原始 URL
     * - IMAGE→REFERENCE 转换：包一层 {"type": "reference", "url": ...}
     * - VIDEO→REFERENCE 转换：同理
     */
    private Object resolveOutputValue(OutputRef sourceRef, WorkflowEdge edge, PortDef targetPort) {
        PortType sourceType = sourceRef.getDataType();

        // 文本型端口直接传值
        if (sourceType == PortType.PROMPT || sourceType == PortType.NEGATIVE_PROMPT || sourceType == PortType.TEXT) {
            return sourceRef.getUrl();
        }

        // ELEMENT 类型直接返回 element_id（数字字符串），不做 URL 转换
        if (sourceType == PortType.ELEMENT) {
            // 返回 {"element_id": <id>} 格式，与 Kling API 要求一致
            try {
                Object elementId = sourceRef.getUrl();  // url 字段存的是 element_id
                if (elementId != null) {
                    Map<String, Object> elementRef = new HashMap<>();
                    elementRef.put("element_id", elementId);
                    return elementRef;
                }
            } catch (Exception e) {
                log.warn("[resolveOutputValue] ELEMENT 类型解析失败: {}", sourceRef.getUrl());
            }
            return null;
        }

        // 优先用本地代理 URL
        String url = sourceRef.getLocalUrl() != null ? sourceRef.getLocalUrl() : sourceRef.getUrl();

        // 类型转换
        if (targetPort != null) {
            PortType targetType = targetPort.getDataType();
            if (targetType == PortType.REFERENCE && (sourceType == PortType.IMAGE || sourceType == PortType.VIDEO)) {
                // IMAGE/VIDEO → REFERENCE
                Map<String, Object> ref = new HashMap<>();
                ref.put("type", "reference");
                ref.put("url", url);
                return ref;
            }
        }

        return url;
    }

    /**
     * 获取节点的输入端口定义 Map，以端口名为键
     */
    /**
     * 执行引用节点：直接返回父节点指定索引的 URL，不走适配器
     */
    private Map<String, OutputRef> executeReferenceNode(WorkflowNode node) {
        Map<String, Object> params = node.getParams();
        if (params == null) {
            throw new IllegalArgumentException("引用节点缺少参数: " + node.getId());
        }
        String parentNodeId = (String) params.get("parentNodeId");
        Integer imageIndex = (Integer) params.get("imageIndex");
        if (parentNodeId == null || imageIndex == null) {
            throw new IllegalArgumentException("引用节点缺少 parentNodeId 或 imageIndex: " + node.getId());
        }

        // 从 params 中获取父节点的 URL 列表（前端创建时写入）
        @SuppressWarnings("unchecked")
        List<String> parentUrls = (List<String>) params.get("parentUrls");
        if (parentUrls == null || parentUrls.isEmpty()) {
            // fallback: 从 params.outputUrl 取单值
            String singleUrl = (String) params.get("outputUrl");
            if (singleUrl != null) {
                parentUrls = List.of(singleUrl);
            } else {
                throw new IllegalArgumentException("引用节点缺少 parentUrls: " + node.getId());
            }
        }

        int idx = Math.min(imageIndex, parentUrls.size() - 1);
        String refUrl = parentUrls.get(idx);

        // 转换代理 URL → 原始 URL
        String realUrl = refUrl;
        if (refUrl != null && refUrl.startsWith("/api/v1/media/") && refUrl.endsWith("/file")) {
            try {
                String idStr = refUrl.substring("/api/v1/media/".length(), refUrl.length() - "/file".length());
                Long assetId = Long.parseLong(idStr);
                MediaAsset asset = mediaAssetService.getById(assetId);
                if (asset != null && asset.getUrl() != null) {
                    realUrl = asset.getUrl();
                }
            } catch (Exception e) {
                log.warn("[executeReferenceNode] 解析 assetId 失败: {}", refUrl);
            }
        }

        // 推断 dataType
        String parentType = (String) params.get("parentType");
        PortType dataType = PortType.IMAGE;
        if (parentType != null && parentType.contains("video")) {
            dataType = PortType.VIDEO;
        }

        // 输出端口名取 output
        Map<String, OutputRef> outputs = new HashMap<>();
        outputs.put("output", OutputRef.builder()
                .url(realUrl)
                .dataType(dataType)
                .allUrls(List.of(realUrl))
                .allAssetIds(List.of())
                .build());
        log.info("[executeReferenceNode] nodeId={}, parentNode={}, idx={}, url={}",
                node.getId(), parentNodeId, idx, realUrl);
        return outputs;
    }

    private Map<String, PortDef> getPortDefMap(WorkflowNode node) {
        Map<String, PortDef> map = new HashMap<>();
        if (node.getInputSlots() != null) {
            for (PortDef port : node.getInputSlots()) {
                map.put(port.getName(), port);
            }
        }
        return map;
    }

    /**
     * 从 SecurityContext 获取当前用户 ID
     */
    private Long getUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /**
     * 根据 userId 查用户名
     */
    private String getUsername(Long userId) {
        if (userId == null) return "unknown";
        User user = userMapper.selectById(userId);
        return user != null ? user.getUsername() : "unknown";
    }

    /**
     * 写完成日志（业务层完成，不依赖 HTTP 调用）
     */
    private void logComplete(Long userId, String adapterId, Capability capability,
                             TaskHandle handle, Long projectId, long durationMs, String resultUrl) {
        ApiOperationLog logEntry = new ApiOperationLog();
        logEntry.setUserId(userId);
        logEntry.setUsername(getUsername(userId));
        logEntry.setOperationType("complete");
        logEntry.setAdapterId(adapterId);
        logEntry.setCapability(capability.getCode());
        logEntry.setTaskId(handle.getTaskId());
        logEntry.setProviderTaskId(handle.getProviderTaskId());
        logEntry.setProjectId(projectId);
        logEntry.setPlatformStatus("succeeded");
        logEntry.setResponseStatus(200);
        logEntry.setDurationMs((int) durationMs);
        if (resultUrl != null) {
            logEntry.setResultUrl(resultUrl.length() > 2000
                    ? resultUrl.substring(0, 2000) : resultUrl);
        }
        logEntry.setCreatedAt(OffsetDateTime.now());
        operationLogService.saveAsync(logEntry);
    }

    /**
     * 从执行实例获取项目 ID
     */
    private Long getProjectId(Long executionId) {
        WorkflowExecution execution = executionMapper.selectById(executionId);
        return execution != null ? execution.getProjectId() : null;
    }

    /**
     * 将原始 API 错误信息翻译成用户友好的提示
     */
    private String friendlyErrorMessage(String rawMessage) {
        if (rawMessage == null) return "执行失败";
        
        // Kling 余额不足
        if (rawMessage.contains("1102") || rawMessage.contains("Account balance not enough")) {
            return "API 账户余额不足，请充值后重试";
        }
        // Kling 余额不足（其他码）
        if (rawMessage.contains("1101") || rawMessage.contains("balance")) {
            return "API 账户余额不足，请充值后重试";
        }
        // 速率限制
        if (rawMessage.contains("429") || rawMessage.contains("Too Many Requests") || rawMessage.contains("rate limit")) {
            return "请求过于频繁，请稍后重试";
        }
        // 认证失败
        if (rawMessage.contains("401") || rawMessage.contains("Unauthorized") || rawMessage.contains("authentication")) {
            return "API 认证失败，请检查密钥配置";
        }
        // 参数校验
        if (rawMessage.contains("参数校验失败")) {
            return rawMessage; // 校验错误已经是中文了
        }
        // 网络超时
        if (rawMessage.contains("timeout") || rawMessage.contains("Timeout") || rawMessage.contains("timed out")) {
            return "请求超时，请稍后重试";
        }
        // 其他：截取关键信息，去掉 JSON 包装
        if (rawMessage.startsWith("{")) {
            try {
                var node = objectMapper.readTree(rawMessage);
                var msg = node.get("message");
                if (msg != null) return msg.asText();
            } catch (Exception ignored) {}
        }
        return rawMessage;
    }

    /**
     * 更新节点执行结果
     */
    private void updateNodeResults(Long executionId, Map<String, Map<String, Object>> nodeResults) {
        try {
            WorkflowExecution execution = executionMapper.selectById(executionId);
            execution.setNodeResults(objectMapper.writeValueAsString(nodeResults));
            executionMapper.updateById(execution);
        } catch (Exception e) {
            log.error("更新节点结果失败, executionId={}", executionId, e);
        }
    }

    /**
     * 标记执行完成
     */
    private void markCompleted(Long executionId) {
        WorkflowExecution execution = executionMapper.selectById(executionId);
        execution.setStatus("completed");
        execution.setCompletedAt(OffsetDateTime.now());
        executionMapper.updateById(execution);
    }

    /**
     * 标记执行失败
     */
    private void markFailed(Long executionId, String errorMsg) {
        try {
            WorkflowExecution execution = executionMapper.selectById(executionId);
            if (execution != null) {
                execution.setStatus("failed");
                execution.setErrorMsg(errorMsg);
                execution.setCompletedAt(OffsetDateTime.now());
                executionMapper.updateById(execution);
            }
        } catch (Exception e) {
            log.error("标记失败状态异常, executionId={}", executionId, e);
        }
    }

    // ==================== 单节点执行 ====================

    /**
     * 执行单个节点
     * <p>
     * 只执行指定的节点。如果该节点有上游输入，尝试从最近一次成功的执行结果中获取。
     * 前端通过 SSE 接收节点状态事件（复用现有 SSE 推送）。
     *
     * @param executionId 执行实例 ID
     * @param workflow     工作流
     * @param nodeId       要执行的节点 ID
     */
    private void executeSingleNode(Long executionId, Workflow workflow, String nodeId) {
        WorkflowExecution execution = executionMapper.selectById(executionId);
        String nodesJson = execution.getDagSnapshot();
        String edgesJson = workflow.getEdges();

        List<WorkflowNode> nodes;
        List<WorkflowEdge> edges;
        try {
            nodes = objectMapper.readValue(nodesJson, new TypeReference<>() {});
            edges = objectMapper.readValue(edgesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析工作流 JSON 失败", e);
        }

        // 找到目标节点
        WorkflowNode targetNode = nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeId));

        log.info("单节点执行开始, executionId={}, nodeId={}, type={}", executionId, nodeId, targetNode.getType());

        // 推送：执行开始
        sseService.sendExecutionStarted(executionId, workflow.getId(), 1);

        // 从节点 params.outputUrl 构建上游输出（持久化的媒体引用）
        Map<String, Map<String, OutputRef>> allOutputs = loadPreviousOutputs(nodes, edges);

        // 构建节点 Map
        Map<String, WorkflowNode> nodeMap = nodes.stream()
                .collect(Collectors.toMap(WorkflowNode::getId, n -> n));

        // 节点执行结果
        Map<String, Map<String, Object>> nodeResults = new ConcurrentHashMap<>();

        try {
            // 设置 SecurityContext
            Long userId = getUserId();
            if (userId != null) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            // 推送：节点开始
            sseService.sendNodeStarted(executionId, nodeId, targetNode.getType());

            // 执行节点
            Map<String, OutputRef> outputs = executeNode(executionId, targetNode, edges, allOutputs, nodes);

            // 记录结果
            Map<String, Object> result = new HashMap<>();
            result.put("status", "completed");
            if (!outputs.isEmpty()) {
                OutputRef firstOutput = outputs.values().iterator().next();
                result.put("outputUrl", firstOutput.getUrl());
                result.put("assetId", firstOutput.getAssetId());
                result.put("dataType", firstOutput.getDataType() != null
                        ? firstOutput.getDataType().getCode() : null);
            }
            nodeResults.put(nodeId, result);

            // 推送：节点完成（含多结果）
            OutputRef firstOutput = outputs.isEmpty() ? null : outputs.values().iterator().next();
            String nodeOutputUrl = firstOutput != null ? firstOutput.getUrl() : null;
            Long nodeAssetId = firstOutput != null ? firstOutput.getAssetId() : null;
            String displayUrl = nodeAssetId != null
                    ? "/api/v1/media/" + nodeAssetId + "/file"
                    : nodeOutputUrl;
            // 构建多结果 display URL 列表
            List<String> displayUrls = null;
            List<Long> displayAssetIds = null;
            if (firstOutput != null && firstOutput.getAllUrls() != null && firstOutput.getAllUrls().size() > 1) {
                displayUrls = new ArrayList<>();
                displayAssetIds = new ArrayList<>();
                for (int i = 0; i < firstOutput.getAllUrls().size(); i++) {
                    String url = firstOutput.getAllUrls().get(i);
                    Long aid = i < firstOutput.getAllAssetIds().size() ? firstOutput.getAllAssetIds().get(i) : null;
                    String du = aid != null ? "/api/v1/media/" + aid + "/file" : url;
                    displayUrls.add(du);
                    displayAssetIds.add(aid);
                }
            }
            sseService.sendNodeCompleted(executionId, nodeId, targetNode.getType(),
                    displayUrl, nodeAssetId, displayUrls, displayAssetIds);

        } catch (Exception e) {
            log.error("单节点执行失败, nodeId={}", nodeId, e);
            String friendlyMsg = friendlyErrorMessage(e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("status", "failed");
            result.put("error", friendlyMsg);
            nodeResults.put(nodeId, result);

            sseService.sendNodeFailed(executionId, nodeId, friendlyMsg);
            throw new RuntimeException("节点 " + nodeId + " 执行失败: " + friendlyMsg, e);
        } finally {
            SecurityContextHolder.clearContext();
        }

        // 更新执行结果
        updateNodeResults(executionId, nodeResults);
        markCompleted(executionId);

        // 推送：执行完成
        sseService.sendExecutionCompleted(executionId);
        log.info("单节点执行完成, executionId={}, nodeId={}", executionId, nodeId);
    }

    /**
     * 从节点 params.outputUrl 构建上游输出（持久化的媒体引用）
     * <p>
     * 用于单节点执行场景：用户只重跑一个节点，但该节点依赖上游输入。
     * 直接从工作流中节点的 params.outputUrl 读取，不依赖执行历史。
     *
     * @param nodes  当前工作流的所有节点
     * @param edges  当前边列表
     * @return 上游输出映射（nodeId -> slotName -> OutputRef）
     */
    private Map<String, Map<String, OutputRef>> loadPreviousOutputs(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        Map<String, Map<String, OutputRef>> allOutputs = new HashMap<>();

        for (WorkflowNode node : nodes) {
            if (node.getParams() == null) continue;

            // 特殊处理：TextInput 节点的值在 params.text 中，不是 outputUrl
            if (NodeType.TEXT_INPUT.equals(node.getType())) {
                String text = (String) node.getParams().get("text");
                if (text != null && !text.isBlank()) {
                    Map<String, OutputRef> outputs = new HashMap<>();
                    outputs.put("prompt", OutputRef.builder()
                            .url(text)
                            .dataType(PortType.PROMPT)
                            .build());
                    allOutputs.put(node.getId(), outputs);
                    log.info("[loadPreviousOutputs] TextInput {} -> text: {}", node.getId(),
                            text.length() > 50 ? text.substring(0, 50) + "..." : text);
                }
                continue;
            }

            // 读取持久化的输出 URL（支持单值和多值）
            String outputUrl = (String) node.getParams().get("outputUrl");
            @SuppressWarnings("unchecked")
            List<String> outputUrls = (List<String>) node.getParams().get("outputUrls");
            if (outputUrl == null && (outputUrls == null || outputUrls.isEmpty())) continue;

            // 统一成列表处理
            List<String> allPersistedUrls = new ArrayList<>();
            if (outputUrls != null) {
                allPersistedUrls.addAll(outputUrls);
            } else if (outputUrl != null) {
                allPersistedUrls.add(outputUrl);
            }

            // 转换代理 URL → 原始 URL
            List<String> realUrls = new ArrayList<>();
            for (String url : allPersistedUrls) {
                String realUrl = url;
                if (url != null && url.startsWith("/api/v1/media/") && url.endsWith("/file")) {
                    try {
                        String idStr = url.substring("/api/v1/media/".length(), url.length() - "/file".length());
                        Long assetId = Long.parseLong(idStr);
                        MediaAsset asset = mediaAssetService.getById(assetId);
                        if (asset != null && asset.getUrl() != null) {
                            realUrl = asset.getUrl();
                        }
                    } catch (Exception e) {
                        log.warn("[loadPreviousOutputs] 解析 assetId 失败: {}", url);
                    }
                }
                if (realUrl != null) realUrls.add(realUrl);
            }
            if (realUrls.isEmpty()) continue;

            // 从节点类型推断 dataType
            String nodeType = node.getType();
            PortType dataType = PortType.VIDEO;
            if (nodeType != null) {
                if (nodeType.contains("image")) dataType = PortType.IMAGE;
                else if (nodeType.contains("video")) dataType = PortType.VIDEO;
            }

            // 从边中找到输出 slot 名称
            String outputSlotName = edges.stream()
                    .filter(e -> e.getSourceNodeId().equals(node.getId()))
                    .map(WorkflowEdge::getSourceSlot)
                    .findFirst()
                    .orElse("output");

            // 构建 OutputRef（含多结果）
            List<Long> allAssetIds = new ArrayList<>();
            for (String url : realUrls) {
                allAssetIds.add(null); // 占位，loadPreviousOutputs 不需要 assetId
            }

            Map<String, OutputRef> outputs = new HashMap<>();
            outputs.put(outputSlotName, OutputRef.builder()
                    .url(realUrls.get(0))
                    .dataType(dataType)
                    .allUrls(realUrls)
                    .allAssetIds(allAssetIds)
                    .build());
            allOutputs.put(node.getId(), outputs);
        }

        // 处理引用节点：从父节点的 outputUrls 中取特定索引
        for (WorkflowNode node : nodes) {
            if (!"reference".equals(node.getType())) continue;
            Map<String, Object> refParams = node.getParams();
            if (refParams == null) continue;
            String parentNodeId = (String) refParams.get("parentNodeId");
            Integer imageIndex = (Integer) refParams.get("imageIndex");
            if (parentNodeId == null || imageIndex == null) continue;

            // 从已加载的父节点输出中取特定索引
            Map<String, OutputRef> parentOutputs = allOutputs.get(parentNodeId);
            if (parentOutputs == null) continue;

            for (Map.Entry<String, OutputRef> entry : parentOutputs.entrySet()) {
                OutputRef parentRef = entry.getValue();
                List<String> allUrls = parentRef.getAllUrls();
                if (allUrls == null || allUrls.isEmpty()) continue;

                int idx = Math.min(imageIndex, allUrls.size() - 1);
                String refUrl = allUrls.get(idx);

                // 转换代理 URL
                String realUrl = refUrl;
                if (refUrl != null && refUrl.startsWith("/api/v1/media/") && refUrl.endsWith("/file")) {
                    try {
                        String idStr = refUrl.substring("/api/v1/media/".length(), refUrl.length() - "/file".length());
                        Long assetId = Long.parseLong(idStr);
                        MediaAsset asset = mediaAssetService.getById(assetId);
                        if (asset != null && asset.getUrl() != null) {
                            realUrl = asset.getUrl();
                        }
                    } catch (Exception e) {
                        log.warn("[loadPreviousOutputs] ref node 解析 assetId 失败: {}", refUrl);
                    }
                }

                PortType dataType = parentRef.getDataType();
                Map<String, OutputRef> refOutputs = new HashMap<>();
                refOutputs.put(entry.getKey(), OutputRef.builder()
                        .url(realUrl)
                        .dataType(dataType)
                        .allUrls(List.of(realUrl))
                        .allAssetIds(List.of())
                        .build());
                allOutputs.put(node.getId(), refOutputs);
                log.info("[loadPreviousOutputs] reference node {} -> parent {} idx {} url {}", node.getId(), parentNodeId, idx, realUrl);
            }
        }

        log.info("[loadPreviousOutputs] nodes with output: {}", allOutputs.keySet());
        return allOutputs;
    }
}