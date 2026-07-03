package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.GenerationTask;
import com.luciano.entity.StepType;
import com.luciano.service.GenerationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 生成任务接口
 * <p>
 * 铁律：所有生成任务统一走 GenerationTask，不管角色图/场景图/视频/TTS。
 * 区别只在于 step_type 字段。
 */
@RestController
@RequestMapping("/api/v1/generation-tasks")
@RequiredArgsConstructor
@Slf4j
public class GenerationTaskController {

    private final GenerationTaskService generationTaskService;

    // ==================== 资产图片生成 ====================

    /**
     * 生成角色图片
     */
    @PostMapping("/character-image/{characterId}")
    public Result<GenerationTask> generateCharacterImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long characterId) {
        return Result.ok(generationTaskService.generateCharacterImage(characterId, userId));
    }

    /**
     * 生成场景图片
     */
    @PostMapping("/scene-image/{sceneId}")
    public Result<GenerationTask> generateSceneImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sceneId) {
        return Result.ok(generationTaskService.generateSceneImage(sceneId, userId));
    }

    /**
     * 生成道具图片
     */
    @PostMapping("/prop-image/{propId}")
    public Result<GenerationTask> generatePropImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long propId) {
        return Result.ok(generationTaskService.generatePropImage(propId, userId));
    }

    // ==================== 分镜生成 ====================

    /**
     * 生成分镜首帧图片
     */
    @PostMapping("/first-frame/{storyboardId}")
    public Result<GenerationTask> generateFirstFrame(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyboardId) {
        return Result.ok(generationTaskService.generateFirstFrame(storyboardId, userId));
    }

    /**
     * 生成分镜尾帧图片
     */
    @PostMapping("/last-frame/{storyboardId}")
    public Result<GenerationTask> generateLastFrame(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyboardId) {
        return Result.ok(generationTaskService.generateLastFrame(storyboardId, userId));
    }

    /**
     * 生成分镜视频
     */
    @PostMapping("/video/{storyboardId}")
    public Result<GenerationTask> generateVideo(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyboardId) {
        return Result.ok(generationTaskService.generateVideo(storyboardId, userId));
    }

    // ==================== 批量生成 ====================

    /**
     * 批量生成资产图片
     * body: { "assetType": "character|scene|prop", "assetIds": [1, 2, 3] }
     */
    @PostMapping("/batch/asset-images")
    public Result<List<GenerationTask>> batchGenerateAssetImages(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, Object> body) {
        String assetType = (String) body.get("assetType");
        @SuppressWarnings("unchecked")
        List<Number> assetIds = (List<Number>) body.get("assetIds");

        List<GenerationTask> tasks = new java.util.ArrayList<>();
        for (Number id : assetIds) {
            Long assetId = id.longValue();
            GenerationTask task = switch (assetType) {
                case "character" -> generationTaskService.generateCharacterImage(assetId, userId);
                case "scene" -> generationTaskService.generateSceneImage(assetId, userId);
                case "prop" -> generationTaskService.generatePropImage(assetId, userId);
                default -> throw new IllegalArgumentException("Unknown asset type: " + assetType);
            };
            tasks.add(task);
        }
        return Result.ok(tasks);
    }

    /**
     * 批量生成分镜视频
     * body: { "storyboardIds": [1, 2, 3] }
     */
    @PostMapping("/batch/videos")
    public Result<List<GenerationTask>> batchGenerateVideos(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> storyboardIds = (List<Number>) body.get("storyboardIds");

        List<GenerationTask> tasks = new java.util.ArrayList<>();
        for (Number id : storyboardIds) {
            tasks.add(generationTaskService.generateVideo(id.longValue(), userId));
        }
        return Result.ok(tasks);
    }

    // ==================== 查询 & 轮询 ====================

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public Result<GenerationTask> get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return Result.ok(generationTaskService.getById(id));
    }

    /**
     * 获取项目的所有生成任务
     */
    @GetMapping("/project/{projectId}")
    public Result<List<GenerationTask>> listByProject(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long projectId) {
        return Result.ok(generationTaskService.listByProjectId(projectId));
    }

    /**
     * 获取分镜的所有生成任务
     */
    @GetMapping("/storyboard/{storyboardId}")
    public Result<List<GenerationTask>> listByStoryboard(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long storyboardId) {
        return Result.ok(generationTaskService.listByStoryboardId(storyboardId));
    }

    /**
     * 轮询任务结果
     * 根据 step_type 自动路由到对应 SPI 的 poll 方法
     */
    @GetMapping("/{id}/poll")
    public Result<GenerationTask> poll(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return Result.ok(generationTaskService.poll(id, userId));
    }

    /**
     * 获取待处理任务
     */
    @GetMapping("/pending")
    public Result<List<GenerationTask>> listPending(@AuthenticationPrincipal Long userId) {
        return Result.ok(generationTaskService.listPendingTasks());
    }

    // ==================== 步骤类型查询 ====================

    /**
     * 获取所有步骤类型
     */
    @GetMapping("/step-types")
    public Result<StepType[]> listStepTypes() {
        return Result.ok(StepType.values());
    }
}