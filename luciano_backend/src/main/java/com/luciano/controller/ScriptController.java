package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.dto.script.ScriptCreateRequest;
import com.luciano.dto.script.ScriptResponse;
import com.luciano.dto.script.ScriptUpdateRequest;
import com.luciano.entity.Script;
import com.luciano.service.ScriptParserService;
import com.luciano.service.ScriptService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 剧本大纲接口
 * 支持手动编辑和 Agent 自动生成两种模式
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/script")
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    /**
     * 获取项目的剧本大纲
     */
    @GetMapping
    public Result<ScriptResponse> get(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long projectId) {
        Script script = scriptService.getByProjectId(projectId);
        if (script == null) {
            return Result.ok(null);
        }
        return Result.ok(scriptService.toResponse(script));
    }

    /**
     * 创建剧本大纲
     * generationMode = "manual" → 手动创建草稿
     * generationMode = "agent"（默认） → 提交 Agent 自动生成
     */
    @PostMapping
    public Result<ScriptResponse> create(@AuthenticationPrincipal Long userId,
                                          @PathVariable Long projectId,
                                          @Valid @RequestBody ScriptCreateRequest request) {
        if ("manual".equals(request.getGenerationMode())) {
            return Result.ok(scriptService.createManual(projectId, userId, request));
        } else {
            return Result.ok(scriptService.createFromIdea(projectId, userId, request));
        }
    }

    /**
     * 更新剧本大纲（手动编辑）
     */
    @PatchMapping
    public Result<ScriptResponse> update(@AuthenticationPrincipal Long userId,
                                          @PathVariable Long projectId,
                                          @RequestBody ScriptUpdateRequest request) {
        return Result.ok(scriptService.updateScript(projectId, userId, request));
    }

    /**
     * 轮询 Agent 生成结果
     * 前端定时调用，直到 status != "generating"
     */
    @GetMapping("/poll")
    public Result<ScriptResponse> poll(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long projectId) {
        ScriptResponse response = scriptService.pollAgentResult(projectId, userId);
        if (response == null) {
            return Result.ok(null);
        }
        return Result.ok(response);
    }

    /**
     * 重新生成（基于现有创意重新提交 Agent）
     */
    @PostMapping("/regenerate")
    public Result<ScriptResponse> regenerate(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long projectId) {
        return Result.ok(scriptService.regenerate(projectId, userId));
    }

    /**
     * 从剧本内容解析生成资产和分镜
     * Agent 生成剧本后，调用此接口将剧本文本结构化为角色/场景/道具/分镜。
     *
     * @param episodeId 集ID（短剧必传，短视频传null）
     */
    @PostMapping("/generate-assets")
    public Result<ScriptParserService.ParseResult> generateAssets(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @RequestParam(required = false) Long episodeId) {
        return Result.ok(scriptService.generateAssetsFromScript(projectId, userId, episodeId));
    }
}