package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.dto.script.ScriptCreateRequest;
import com.luciano.dto.script.ScriptResponse;
import com.luciano.dto.script.ScriptUpdateRequest;
import com.luciano.entity.ModelConfig;
import com.luciano.entity.Script;
import com.luciano.repository.mapper.ScriptMapper;
import com.luciano.spi.AgentGenerator;
import com.luciano.spi.request.AgentSubmitRequest;
import com.luciano.spi.response.AgentPollResult;
import com.luciano.spi.response.AgentSubmitResult;
import com.luciano.spi.response.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 剧本大纲服务
 * 支持手动编辑和 Agent 自动生成
 * 支持从剧本文本解析生成资产和分镜
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService extends ServiceImpl<ScriptMapper, Script> {

    private final ModelGateway modelGateway;
    private final ModelConfigService modelConfigService;
    private final ScriptParserService scriptParserService;

    /**
     * 获取项目的剧本大纲
     */
    public Script getByProjectId(Long projectId) {
        LambdaQueryWrapper<Script> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Script::getProjectId, projectId)
               .orderByDesc(Script::getVersion)
               .last("LIMIT 1");
        return getOne(wrapper, false);
    }

    /**
     * 创建剧本大纲（手动模式）
     */
    @Transactional
    public ScriptResponse createManual(Long projectId, Long userId, ScriptCreateRequest request) {
        Script script = new Script();
        script.setProjectId(projectId);
        script.setOriginalIdea(request.getOriginalIdea());
        script.setGenerationMode("manual");
        script.setSource("user");
        script.setStatus("draft");
        script.setVersion(1);
        save(script);

        log.info("[Script] Manual script created: id={}, projectId={}, userId={}",
                script.getId(), projectId, userId);
        return toResponse(script);
    }

    /**
     * 创建剧本大纲（Agent 模式）
     * 提交创意 → 调用 Agent → 异步轮询
     */
    @Transactional
    public ScriptResponse createFromIdea(Long projectId, Long userId, ScriptCreateRequest request) {
        // 1. 保存原始创意
        Script script = new Script();
        script.setProjectId(projectId);
        script.setOriginalIdea(request.getOriginalIdea());
        script.setGenerationMode("agent");
        script.setSource("agent");
        script.setStatus("generating");
        script.setVersion(1);
        save(script);

        // 2. 从步骤级模型配置获取 Agent 提供商
        ModelConfig config = modelConfigService.getConfigForStep(projectId, "script_generation");
        AgentGenerator agent = modelGateway.getAgentGenerator(
                modelConfigService.getProviderName(config.getProviderId()));

        // 3. 构建 Agent 请求
        String prompt = buildScriptPrompt(request.getOriginalIdea());
        AgentSubmitRequest submitRequest = AgentSubmitRequest.builder()
                .prompt(prompt)
                .threadId(script.getAgentThreadId())  // 复用会话
                .build();

        // 4. 提交 Agent 任务
        try {
            AgentSubmitResult result = agent.submit(submitRequest);
            script.setAgentThreadId(result.getThreadId());
            script.setAgentRunId(result.getRunId());
            updateById(script);

            log.info("[Script] Agent task submitted: id={}, threadId={}, runId={}",
                    script.getId(), result.getThreadId(), result.getRunId());
        } catch (Exception e) {
            script.setStatus("failed");
            updateById(script);
            log.error("[Script] Agent submit failed: id={}, error={}", script.getId(), e.getMessage());
            throw new RuntimeException("剧本生成提交失败: " + e.getMessage(), e);
        }

        return toResponse(script);
    }

    /**
     * 轮询 Agent 生成结果
     */
    public ScriptResponse pollAgentResult(Long projectId, Long userId) {
        Script script = getByProjectId(projectId);
        if (script == null) {
            return null;
        }

        if (!"generating".equals(script.getStatus())) {
            return toResponse(script);
        }

        // 轮询 Agent
        AgentGenerator agent = modelGateway.getAgentGenerator(
                modelConfigService.getProviderName(
                        modelConfigService.getConfigForStep(projectId, "script_generation").getProviderId()));

        try {
            AgentPollResult pollResult = agent.poll(script.getAgentThreadId(), script.getAgentRunId());

            if (pollResult.getStatus() == TaskStatus.COMPLETED) {
                // 提取生成结果
                String generatedText = extractGeneratedText(pollResult);
                script.setFullScript(generatedText);
                script.setStatus("completed");
                script.setUpdatedAt(OffsetDateTime.now());
                updateById(script);
                log.info("[Script] Agent generation completed: id={}", script.getId());
            } else if (pollResult.getStatus() == TaskStatus.FAILED) {
                script.setStatus("failed");
                script.setUpdatedAt(OffsetDateTime.now());
                updateById(script);
                log.error("[Script] Agent generation failed: id={}, error={}",
                        script.getId(), pollResult.getErrorMsg());
            }
            // 还在 processing 中，不更新
        } catch (Exception e) {
            log.error("[Script] Agent poll error: id={}, error={}", script.getId(), e.getMessage());
            // 不改状态，下次轮询再试
        }

        return toResponse(script);
    }

    /**
     * 更新剧本大纲（手动编辑）
     */
    @Transactional
    public ScriptResponse updateScript(Long projectId, Long userId, ScriptUpdateRequest request) {
        Script script = getByProjectId(projectId);
        if (script == null) {
            throw new RuntimeException("项目没有剧本大纲");
        }

        if (request.getOriginalIdea() != null) {
            script.setOriginalIdea(request.getOriginalIdea());
        }
        if (request.getSummary() != null) {
            script.setSummary(request.getSummary());
        }
        if (request.getFullScript() != null) {
            script.setFullScript(request.getFullScript());
        }
        if (request.getGenerationMode() != null) {
            script.setGenerationMode(request.getGenerationMode());
        }
        if (request.getStatus() != null) {
            script.setStatus(request.getStatus());
        }
        script.setSource("user");
        script.setUpdatedAt(OffsetDateTime.now());
        updateById(script);

        log.info("[Script] Script updated: id={}, projectId={}", script.getId(), projectId);
        return toResponse(script);
    }

    /**
     * 重新生成（基于现有创意重新提交 Agent）
     */
    @Transactional
    public ScriptResponse regenerate(Long projectId, Long userId) {
        Script script = getByProjectId(projectId);
        if (script == null) {
            throw new RuntimeException("项目没有剧本大纲");
        }
        if (script.getOriginalIdea() == null || script.getOriginalIdea().isBlank()) {
            throw new RuntimeException("原始创意为空，无法重新生成");
        }

        // 版本号+1
        script.setVersion(script.getVersion() + 1);
        script.setStatus("generating");
        script.setFullScript(null);

        // 复用 Agent 会话
        AgentGenerator agent = modelGateway.getAgentGenerator(
                modelConfigService.getProviderName(
                        modelConfigService.getConfigForStep(projectId, "script_generation").getProviderId()));

        String prompt = buildScriptPrompt(script.getOriginalIdea());
        AgentSubmitRequest submitRequest = AgentSubmitRequest.builder()
                .prompt(prompt)
                .threadId(script.getAgentThreadId())
                .build();

        try {
            AgentSubmitResult result = agent.submit(submitRequest);
            script.setAgentThreadId(result.getThreadId());
            script.setAgentRunId(result.getRunId());
            updateById(script);
        } catch (Exception e) {
            script.setStatus("failed");
            updateById(script);
            throw new RuntimeException("重新生成提交失败: " + e.getMessage(), e);
        }

        return toResponse(script);
    }

    // ==================== 私有方法 ====================

    private String buildScriptPrompt(String idea) {
        return "根据以下创意生成短剧剧本大纲，包含角色设定、场景描述、剧情走向和分集概要：\n\n" + idea;
    }

    private String extractGeneratedText(AgentPollResult pollResult) {
        if (pollResult.getArtifacts() == null) {
            return null;
        }
        // 优先取 text 字段
        Object text = pollResult.getArtifacts().get("text");
        if (text != null) {
            return text.toString();
        }
        // 尝试取 content
        Object content = pollResult.getArtifacts().get("content");
        if (content != null) {
            return content.toString();
        }
        // 尝试取 response
        Object response = pollResult.getArtifacts().get("response");
        if (response != null) {
            return response.toString();
        }
        return pollResult.getArtifacts().toString();
    }

    public ScriptResponse toResponse(Script script) {
        return ScriptResponse.builder()
                .id(script.getId())
                .projectId(script.getProjectId())
                .originalIdea(script.getOriginalIdea())
                .summary(script.getSummary())
                .fullScript(script.getFullScript())
                .generationMode(script.getGenerationMode())
                .source(script.getSource())
                .status(script.getStatus())
                .agentThreadId(script.getAgentThreadId())
                .agentRunId(script.getAgentRunId())
                .version(script.getVersion())
                .createdAt(script.getCreatedAt())
                .updatedAt(script.getUpdatedAt())
                .build();
    }

    // ==================== 剧本解析生成 ====================

    /**
     * 从剧本内容解析并创建资产和分镜
     * Agent 生成剧本后，调用此方法将剧本文本结构化。
     * <p>
     * 铁律：所有资产走统一表，generation_mode=agent 标记来源。
     *
     * @param projectId 项目ID
     * @param userId     创建者ID
     * @param episodeId  集ID（短剧必须有，短视频传null）
     * @return 解析结果
     */
    @Transactional
    public ScriptParserService.ParseResult generateAssetsFromScript(Long projectId, Long userId, Long episodeId) {
        Script script = getByProjectId(projectId);
        if (script == null) {
            throw new RuntimeException("项目没有剧本大纲，请先生成或创建剧本");
        }
        if (script.getFullScript() == null || script.getFullScript().isBlank()) {
            throw new RuntimeException("剧本内容为空，无法解析生成资产");
        }

        log.info("[Script] Parsing script to assets: projectId={}, scriptId={}", projectId, script.getId());

        ScriptParserService.ParseResult result = scriptParserService.parseAndCreate(
                projectId, userId, episodeId, script.getFullScript());

        // 更新剧本状态为已解析
        script.setStatus("parsed");
        script.setUpdatedAt(OffsetDateTime.now());
        updateById(script);

        log.info("[Script] Assets generated: characters={}, scenes={}, props={}, storyboards={}",
                result.getCharacters().size(), result.getScenes().size(),
                result.getProps().size(), result.getStoryboards().size());

        return result;
    }
}