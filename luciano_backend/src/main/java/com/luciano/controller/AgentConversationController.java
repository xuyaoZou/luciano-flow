package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.AgentConversation;
import com.luciano.entity.AgentMessage;
import com.luciano.entity.MediaAsset;
import com.luciano.service.AgentConversationService;
import com.luciano.service.AgentConversationService.PollConversationResult;
import com.luciano.service.AgentConversationService.SendMessageResult;
import com.luciano.service.AgentMessageService;
import com.luciano.service.MediaAssetService;
import com.luciano.dto.response.AgentMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent 模式会话控制器
 * 中转站：转发用户消息给 AI Agent，提取媒体文件存入资源库
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/conversations")
@RequiredArgsConstructor
public class AgentConversationController {

    private final AgentConversationService conversationService;
    private final AgentMessageService messageService;
    private final MediaAssetService mediaAssetService;

    /**
     * 创建新的 Agent 会话
     * POST /api/v1/agent/conversations
     */
    @PostMapping
    public Result<AgentConversation> createConversation(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, Object> body) {
        Long projectId = Long.valueOf(body.get("projectId").toString());
        String provider = (String) body.getOrDefault("provider", "xyq");

        // 每次都创建新会话（创作视图每次发送是新起点）
        AgentConversation conv = conversationService.createConversation(projectId, userId, provider, null);
        log.info("[AgentConv] Created new conversation id={}, project={}", conv.getId(), projectId);
        return Result.ok(conv);
    }

    /**
     * 发送消息给 Agent
     * POST /api/v1/agent/conversations/{id}/message
     */
    @PostMapping("/{id}/message")
    public Result<SendMessageResult> sendMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");

        SendMessageResult result = conversationService.sendMessage(id, message, userId);
        return Result.ok(result);
    }

    /**
     * 轮询会话状态
     * GET /api/v1/agent/conversations/{id}/poll?runId=xxx
     */
    @GetMapping("/{id}/poll")
    public Result<PollConversationResult> pollConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam String runId) {
        PollConversationResult result = conversationService.pollConversation(id, runId);
        return Result.ok(result);
    }

    /**
     * 获取会话产出的媒体资产
     * GET /api/v1/agent/conversations/{id}/assets
     */
    @GetMapping("/{id}/assets")
    public Result<List<MediaAsset>> getConversationAssets(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) String runId) {
        List<MediaAsset> assets;
        if (runId != null && !runId.isBlank()) {
            assets = mediaAssetService.listByConversationIdAndRunId(id, runId);
        } else {
            assets = mediaAssetService.listByConversationId(id);
        }
        return Result.ok(assets);
    }

    /**
     * 获取会话消息历史
     * GET /api/v1/agent/conversations/{id}/messages
     */
    @GetMapping("/{id}/messages")
    public Result<List<AgentMessageVO>> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        List<AgentMessage> messages = messageService.listByConversationId(id);
        List<AgentMessageVO> vos = messages.stream().map(msg -> {
            AgentMessageVO vo = new AgentMessageVO();
            vo.setId(msg.getId());
            vo.setConversationId(msg.getConversationId());
            vo.setRole(msg.getRole());
            vo.setContent(msg.getContent());
            vo.setText(msg.getText());
            vo.setRunId(msg.getRunId());
            vo.setStatus(msg.getStatus());
            vo.setErrorMsg(msg.getErrorMsg());
            vo.setMediaCount(msg.getMediaCount());
            vo.setCreatedAt(msg.getCreatedAt());
            vo.setUpdatedAt(msg.getUpdatedAt());
            // 关联查询该消息的媒体资产
            if ("assistant".equals(msg.getRole()) && msg.getId() != null) {
                List<MediaAsset> assets = mediaAssetService.listByAgentMessageId(msg.getId());
                vo.setMediaAssets(assets);
            }
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    /**
     * 获取会话列表
     * GET /api/v1/agent/conversations?projectId=5  — 按项目筛选
     * GET /api/v1/agent/conversations              — 当前用户全部会话
     */
    @GetMapping
    public Result<List<AgentConversation>> listConversations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long projectId) {
        log.info("[AgentConv] listConversations userId={}, projectId={}", userId, projectId);
        if (projectId != null) {
            return Result.ok(conversationService.listByProjectId(projectId));
        }
        return Result.ok(conversationService.listByUserId(userId));
    }

    /**
     * 关闭会话
     * DELETE /api/v1/agent/conversations/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> closeConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        conversationService.closeConversation(id);
        return Result.ok();
    }
}