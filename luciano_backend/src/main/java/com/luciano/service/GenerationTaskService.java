package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.*;
import com.luciano.repository.mapper.GenerationTaskMapper;
import com.luciano.spi.AgentGenerator;
import com.luciano.spi.ImageGenerator;
import com.luciano.spi.VideoGenerator;
import com.luciano.spi.request.AgentSubmitRequest;
import com.luciano.spi.request.ImageGenerateRequest;
import com.luciano.spi.request.VideoGenerateRequest;
import com.luciano.spi.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 生成任务服务
 * <p>
 * 铁律：所有生成任务统一走此服务，不管角色图/场景图/视频/TTS。
 * 按步骤类型(step_type)路由到对应 SPI 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationTaskService extends ServiceImpl<GenerationTaskMapper, GenerationTask> {

    private final ModelGateway modelGateway;
    private final ModelConfigService modelConfigService;
    private final PromptBuilderService promptBuilderService;
    private final CharacterAssetService characterAssetService;
    private final SceneAssetService sceneAssetService;
    private final PropAssetService propAssetService;
    private final StoryboardService storyboardService;
    private final EpisodeService episodeService;
    private final ProjectService projectService;

    // ==================== 查询 ====================

    public List<GenerationTask> listByProjectId(Long projectId) {
        LambdaQueryWrapper<GenerationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationTask::getProjectId, projectId)
               .orderByDesc(GenerationTask::getCreatedAt);
        return list(wrapper);
    }

    public List<GenerationTask> listByStoryboardId(Long storyboardId) {
        LambdaQueryWrapper<GenerationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationTask::getStoryboardId, storyboardId)
               .orderByDesc(GenerationTask::getCreatedAt);
        return list(wrapper);
    }

    public List<GenerationTask> listPendingTasks() {
        LambdaQueryWrapper<GenerationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationTask::getStatus, "pending")
               .or().eq(GenerationTask::getStatus, "processing")
               .orderByAsc(GenerationTask::getCreatedAt);
        return list(wrapper);
    }

    /**
     * 按 batch_id 查询任务
     */
    public List<GenerationTask> listByBatchId(String batchId) {
        LambdaQueryWrapper<GenerationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationTask::getConfig, batchId)  // batch_id 暂存 config JSONB
               .orderByAsc(GenerationTask::getCreatedAt);
        return list(wrapper);
    }

    // ==================== 资产图片生成 ====================

    /**
     * 生成角色图片
     */
    @Transactional
    public GenerationTask generateCharacterImage(Long characterId, Long userId) {
        CharacterAsset character = characterAssetService.getById(characterId);
        if (character == null) {
            throw new RuntimeException("角色不存在: " + characterId);
        }

        Project project = character.getProjectId() != null ? projectService.getById(character.getProjectId()) : null;

        // 获取步骤级模型配置
        ModelConfig config = modelConfigService.getConfigForStep(
                character.getProjectId(), StepType.CHARACTER_IMAGE.getCode());

        // 构建提示词（后端构建！）
        String prompt = promptBuilderService.buildCharacterImagePrompt(character, project);

        // 创建任务
        GenerationTask task = createTask(
                character.getProjectId(), null, null, characterId,
                userId, StepType.CHARACTER_IMAGE.getCode(),
                config, prompt, null
        );

        // 提交图片生成
        submitImageTask(task, config, prompt, character.getReferenceImages());

        return task;
    }

    /**
     * 生成场景图片
     */
    @Transactional
    public GenerationTask generateSceneImage(Long sceneId, Long userId) {
        SceneAsset scene = sceneAssetService.getById(sceneId);
        if (scene == null) {
            throw new RuntimeException("场景不存在: " + sceneId);
        }

        Project project = scene.getProjectId() != null ? projectService.getById(scene.getProjectId()) : null;

        ModelConfig config = modelConfigService.getConfigForStep(
                scene.getProjectId(), StepType.SCENE_IMAGE.getCode());

        String prompt = promptBuilderService.buildSceneImagePrompt(scene, project);

        GenerationTask task = createTask(
                scene.getProjectId(), null, null, sceneId,
                userId, StepType.SCENE_IMAGE.getCode(),
                config, prompt, null
        );

        submitImageTask(task, config, prompt, scene.getReferenceImages());

        return task;
    }

    /**
     * 生成道具图片
     */
    @Transactional
    public GenerationTask generatePropImage(Long propId, Long userId) {
        PropAsset prop = propAssetService.getById(propId);
        if (prop == null) {
            throw new RuntimeException("道具不存在: " + propId);
        }

        Project project = prop.getProjectId() != null ? projectService.getById(prop.getProjectId()) : null;

        ModelConfig config = modelConfigService.getConfigForStep(
                prop.getProjectId(), StepType.PROP_IMAGE.getCode());

        String prompt = promptBuilderService.buildPropImagePrompt(prop, project);

        GenerationTask task = createTask(
                prop.getProjectId(), null, null, propId,
                userId, StepType.PROP_IMAGE.getCode(),
                config, prompt, null
        );

        submitImageTask(task, config, prompt, prop.getReferenceImages());

        return task;
    }

    // ==================== 分镜生成 ====================

    /**
     * 生成分镜首帧图片
     */
    @Transactional
    public GenerationTask generateFirstFrame(Long storyboardId, Long userId) {
        Storyboard sb = storyboardService.getById(storyboardId);
        if (sb == null) {
            throw new RuntimeException("分镜不存在: " + storyboardId);
        }

        ModelConfig config = modelConfigService.getConfigForStep(
                sb.getProjectId(), StepType.FIRST_FRAME.getCode());

        String prompt = promptBuilderService.buildFirstFramePrompt(sb);

        GenerationTask task = createTask(
                sb.getProjectId(), sb.getEpisodeId(), storyboardId, null,
                userId, StepType.FIRST_FRAME.getCode(),
                config, prompt, null
        );

        // 首帧可能有参考图
        ImageGenerateRequest request = ImageGenerateRequest.builder()
                .prompt(prompt)
                .build();

        submitImageTask(task, config, prompt, null);

        return task;
    }

    /**
     * 生成分镜尾帧图片
     */
    @Transactional
    public GenerationTask generateLastFrame(Long storyboardId, Long userId) {
        Storyboard sb = storyboardService.getById(storyboardId);
        if (sb == null) {
            throw new RuntimeException("分镜不存在: " + storyboardId);
        }

        ModelConfig config = modelConfigService.getConfigForStep(
                sb.getProjectId(), StepType.LAST_FRAME.getCode());

        String prompt = promptBuilderService.buildLastFramePrompt(sb);

        GenerationTask task = createTask(
                sb.getProjectId(), sb.getEpisodeId(), storyboardId, null,
                userId, StepType.LAST_FRAME.getCode(),
                config, prompt, null
        );

        submitImageTask(task, config, prompt, null);

        return task;
    }

    /**
     * 生成分镜视频
     */
    @Transactional
    public GenerationTask generateVideo(Long storyboardId, Long userId) {
        Storyboard sb = storyboardService.getById(storyboardId);
        if (sb == null) {
            throw new RuntimeException("分镜不存在: " + storyboardId);
        }

        ModelConfig config = modelConfigService.getConfigForStep(
                sb.getProjectId(), StepType.VIDEO.getCode());

        // 根据生成模式构建不同提示词
        String prompt;
        if ("express".equals(sb.getGenerationMode())) {
            prompt = promptBuilderService.buildExpressVideoPrompt(sb);
        } else {
            prompt = promptBuilderService.buildManualVideoPrompt(sb);
        }

        GenerationTask task = createTask(
                sb.getProjectId(), sb.getEpisodeId(), storyboardId, null,
                userId, StepType.VIDEO.getCode(),
                config, prompt, null
        );

        // 视频可能有首尾帧参考
        submitVideoTask(task, config, prompt,
                sb.getFirstFrameImageUrl(), sb.getLastFrameImageUrl());

        return task;
    }

    // ==================== 轮询 ====================

    /**
     * 轮询任务结果
     * 根据 step_type 路由到对应的 SPI poll
     */
    public GenerationTask poll(Long taskId, Long userId) {
        GenerationTask task = getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        // 非 processing 状态直接返回
        if (!"processing".equals(task.getStatus())) {
            return task;
        }

        String stepType = task.getTaskType();
        String providerName = task.getProvider() != null ? task.getProvider() : "xyq";

        try {
            switch (stepType) {
                case "character_image", "scene_image", "prop_image", "first_frame", "last_frame" -> {
                    // 图片类型 → ImageGenerator poll
                    pollImageTask(task, providerName);
                }
                case "video" -> {
                    // 视频类型 → VideoGenerator poll
                    pollVideoTask(task, providerName);
                }
                case "script_generation" -> {
                    // Agent 类型 → AgentGenerator poll
                    pollAgentTask(task, providerName);
                }
                default -> log.warn("[GenerationTask] Unknown step type for polling: {}", stepType);
            }
        } catch (Exception e) {
            log.warn("[GenerationTask] Poll failed: id={}, error={}", taskId, e.getMessage());
        }

        return task;
    }

    // ==================== 私有方法 ====================

    private GenerationTask createTask(Long projectId, Long episodeId, Long storyboardId,
                                       Long assetId, Long userId, String stepType,
                                       ModelConfig config, String prompt, String negativePrompt) {
        GenerationTask task = new GenerationTask();
        task.setProjectId(projectId);
        task.setEpisodeId(episodeId);
        task.setStoryboardId(storyboardId);
        task.setAssetId(assetId);
        task.setUserId(userId);
        task.setTaskType(stepType);
        task.setGenerationMode("manual");  // 默认手动，Agent 模式在外层设置
        task.setPrompt(prompt);
        task.setNegativePrompt(negativePrompt);
        task.setStatus("pending");
        task.setProviderSource(config != null ? config.getProviderSource() : "platform");

        if (config != null) {
            task.setProvider(modelConfigService.getProviderName(config.getProviderId()));
            task.setModel(config.getModelName());
        }

        save(task);
        return task;
    }

    private void submitImageTask(GenerationTask task, ModelConfig config,
                                  String prompt, String referenceImagesJson) {
        String providerName = task.getProvider() != null ? task.getProvider() : "xyq";

        try {
            ImageGenerator generator = modelGateway.getImageGenerator(providerName);

            ImageGenerateRequest.ImageGenerateRequestBuilder reqBuilder = ImageGenerateRequest.builder()
                    .prompt(prompt);

            // 参考图（JSONB 字段暂不解析，后续加 Jackson 反序列化）

            // Thread 复用
            String threadId = resolveThreadId(task);
            if (threadId != null) {
                reqBuilder.extra(java.util.Map.of("thread_id", threadId));
            }

            ImageGenerateResult result = generator.generate(reqBuilder.build());

            task.setTaskId(result.getTaskId());
            task.setThreadId(extractThreadId(result.getTaskId()));
            task.setStatus("processing");
            updateById(task);

            log.info("[GenerationTask] Image submitted: id={}, taskId={}", task.getId(), task.getTaskId());

        } catch (Exception e) {
            task.setStatus("failed");
            task.setErrorMsg("图片生成提交失败: " + e.getMessage());
            updateById(task);
            log.error("[GenerationTask] Image submit failed: id={}, error={}", task.getId(), e.getMessage());
        }
    }

    private void submitVideoTask(GenerationTask task, ModelConfig config,
                                  String prompt, String firstFrameUrl, String lastFrameUrl) {
        String providerName = task.getProvider() != null ? task.getProvider() : "xyq";

        try {
            VideoGenerator generator = modelGateway.getVideoGenerator(providerName);

            VideoGenerateRequest.VideoGenerateRequestBuilder reqBuilder = VideoGenerateRequest.builder()
                    .prompt(prompt);

            if (firstFrameUrl != null && !firstFrameUrl.isBlank()) {
                reqBuilder.firstFrameUrl(firstFrameUrl);
            }
            if (lastFrameUrl != null && !lastFrameUrl.isBlank()) {
                reqBuilder.lastFrameUrl(lastFrameUrl);
            }

            // Thread 复用
            String threadId = resolveThreadId(task);
            if (threadId != null) {
                reqBuilder.extra(java.util.Map.of("thread_id", threadId));
            }

            VideoGenerateResult result = generator.generate(reqBuilder.build());

            task.setTaskId(result.getTaskId());
            task.setThreadId(result.getThreadId());
            task.setStatus("processing");
            updateById(task);

            log.info("[GenerationTask] Video submitted: id={}, taskId={}", task.getId(), task.getTaskId());

        } catch (Exception e) {
            task.setStatus("failed");
            task.setErrorMsg("视频生成提交失败: " + e.getMessage());
            updateById(task);
            log.error("[GenerationTask] Video submit failed: id={}, error={}", task.getId(), e.getMessage());
        }
    }

    private void pollImageTask(GenerationTask task, String providerName) {
        if (task.getTaskId() == null) return;

        ImageGenerator generator = modelGateway.getImageGenerator(providerName);
        ImagePollResult result = generator.poll(task.getTaskId());

        switch (result.getStatus()) {
            case COMPLETED -> {
                task.setStatus("completed");
                task.setOutputUrl(result.getOutputUrl());
                task.setCompletedAt(OffsetDateTime.now());
                updateById(task);
                // 更新关联资产/分镜的图片 URL
                updateAssetImageUrl(task, result.getOutputUrl());
            }
            case FAILED -> {
                task.setStatus("failed");
                task.setErrorMsg(result.getErrorMsg());
                task.setCompletedAt(OffsetDateTime.now());
                updateById(task);
            }
            default -> { /* still processing */ }
        }
    }

    private void pollVideoTask(GenerationTask task, String providerName) {
        if (task.getTaskId() == null) return;

        VideoGenerator generator = modelGateway.getVideoGenerator(providerName);
        VideoPollResult result = generator.poll(task.getTaskId());

        switch (result.getStatus()) {
            case COMPLETED -> {
                task.setStatus("completed");
                task.setOutputUrl(result.getOutputUrl());
                task.setCompletedAt(OffsetDateTime.now());
                updateById(task);
                // 更新分镜视频 URL
                updateStoryboardVideoUrl(task, result.getOutputUrl());
            }
            case FAILED -> {
                task.setStatus("failed");
                task.setErrorMsg(result.getErrorMsg());
                task.setCompletedAt(OffsetDateTime.now());
                updateById(task);
            }
            default -> { /* still processing */ }
        }
    }

    private void pollAgentTask(GenerationTask task, String providerName) {
        if (task.getTaskId() == null) return;

        // taskId 格式: threadId:runId
        String[] parts = task.getTaskId().split(":");
        if (parts.length != 2) return;

        AgentGenerator agent = modelGateway.getAgentGenerator(providerName);
        AgentPollResult pollResult = agent.poll(parts[0], parts[1]);

        switch (pollResult.getStatus()) {
            case COMPLETED -> {
                task.setStatus("completed");
                if (pollResult.getArtifacts() != null) {
                    Object url = pollResult.getArtifacts().get("url");
                    task.setOutputUrl(url != null ? url.toString() : null);
                }
                task.setCompletedAt(OffsetDateTime.now());
                updateById(task);
            }
            case FAILED -> {
                task.setStatus("failed");
                task.setErrorMsg(pollResult.getErrorMsg());
                task.setCompletedAt(OffsetDateTime.now());
                updateById(task);
            }
            default -> { /* still processing */ }
        }
    }

    /**
     * 生成完成后，回写资产图片 URL
     */
    private void updateAssetImageUrl(GenerationTask task, String imageUrl) {
        if (imageUrl == null || task.getAssetId() == null) return;

        switch (task.getTaskType()) {
            case "character_image" -> {
                CharacterAsset asset = characterAssetService.getById(task.getAssetId());
                if (asset != null) {
                    asset.setImageUrl(imageUrl);
                    characterAssetService.updateById(asset);
                    log.info("[GenerationTask] Updated character image: assetId={}", task.getAssetId());
                }
            }
            case "scene_image" -> {
                SceneAsset asset = sceneAssetService.getById(task.getAssetId());
                if (asset != null) {
                    asset.setImageUrl(imageUrl);
                    sceneAssetService.updateById(asset);
                    log.info("[GenerationTask] Updated scene image: assetId={}", task.getAssetId());
                }
            }
            case "prop_image" -> {
                PropAsset asset = propAssetService.getById(task.getAssetId());
                if (asset != null) {
                    asset.setImageUrl(imageUrl);
                    propAssetService.updateById(asset);
                    log.info("[GenerationTask] Updated prop image: assetId={}", task.getAssetId());
                }
            }
            case "first_frame" -> {
                if (task.getStoryboardId() != null) {
                    Storyboard sb = storyboardService.getById(task.getStoryboardId());
                    if (sb != null) {
                        sb.setFirstFrameImageUrl(imageUrl);
                        storyboardService.updateById(sb);
                        log.info("[GenerationTask] Updated first frame: storyboardId={}", task.getStoryboardId());
                    }
                }
            }
            case "last_frame" -> {
                if (task.getStoryboardId() != null) {
                    Storyboard sb = storyboardService.getById(task.getStoryboardId());
                    if (sb != null) {
                        sb.setLastFrameImageUrl(imageUrl);
                        storyboardService.updateById(sb);
                        log.info("[GenerationTask] Updated last frame: storyboardId={}", task.getStoryboardId());
                    }
                }
            }
        }
    }

    /**
     * 生成完成后，回写分镜视频 URL
     */
    private void updateStoryboardVideoUrl(GenerationTask task, String videoUrl) {
        if (videoUrl == null || task.getStoryboardId() == null) return;

        Storyboard sb = storyboardService.getById(task.getStoryboardId());
        if (sb != null) {
            sb.setVideoUrl(videoUrl);
            storyboardService.updateById(sb);
            log.info("[GenerationTask] Updated video URL: storyboardId={}", task.getStoryboardId());
        }
    }

    /**
     * 解析 Thread ID 用于复用
     * 优先 storyboard → episode → 最近有 threadId 的 episode
     */
    private String resolveThreadId(GenerationTask task) {
        // 1. 如果任务本身有 threadId（外部传入）
        if (task.getThreadId() != null) {
            return task.getThreadId();
        }

        // 2. 从 storyboard 关联的 episode 获取
        if (task.getStoryboardId() != null) {
            Storyboard sb = storyboardService.getById(task.getStoryboardId());
            if (sb != null && sb.getEpisodeId() != null) {
                Episode ep = episodeService.getById(sb.getEpisodeId());
                if (ep != null && ep.getXyqThreadId() != null) {
                    return ep.getXyqThreadId();
                }
            }
        }

        // 3. 从 episodeId 直接获取
        if (task.getEpisodeId() != null) {
            Episode ep = episodeService.getById(task.getEpisodeId());
            if (ep != null && ep.getXyqThreadId() != null) {
                return ep.getXyqThreadId();
            }
        }

        // 4. 从项目最近有 threadId 的 episode 获取
        if (task.getProjectId() != null) {
            List<Episode> episodes = episodeService.listByProjectId(task.getProjectId());
            for (Episode ep : episodes) {
                if (ep.getXyqThreadId() != null) {
                    return ep.getXyqThreadId();
                }
            }
        }

        return null;  // 无可复用 thread，让 SPI 自行创建
    }

    /**
     * 从 taskId 中提取 threadId（格式: threadId:runId）
     */
    private String extractThreadId(String taskId) {
        if (taskId == null) return null;
        String[] parts = taskId.split(":");
        return parts.length >= 1 ? parts[0] : null;
    }
}