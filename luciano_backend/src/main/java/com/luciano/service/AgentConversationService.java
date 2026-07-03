package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.AgentConversation;
import com.luciano.entity.AgentMessage;
import com.luciano.entity.MediaAsset;
import com.luciano.entity.Project;
import com.luciano.repository.mapper.AgentConversationMapper;
import com.luciano.spi.impl.XyqConversationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationService extends ServiceImpl<AgentConversationMapper, AgentConversation> {

    private final XyqConversationAdapter xyqAdapter;
    private final MediaAssetService mediaAssetService;
    private final MediaDownloadService mediaDownloadService;
    private final ProjectService projectService;
    private final AgentMessageService messageService;

    /**
     * 创建新的 Agent 会话
     * 同时更新 project.context_session_id
     */
    @Transactional
    public AgentConversation createConversation(Long projectId, Long userId, String provider, String contextSessionId) {
        AgentConversation conv = new AgentConversation();
        conv.setProjectId(projectId);
        conv.setUserId(userId);
        conv.setProvider(provider);
        conv.setContextSessionId(contextSessionId);
        conv.setStatus("active");
        conv.setProviderMeta("{}");
        save(conv);

        // 如果有 context_session_id，回写到 project
        if (contextSessionId != null) {
            updateProjectContext(projectId, provider, contextSessionId);
        }

        log.info("[AgentConv] Created conversation id={}, project={}, provider={}, sessionId={}",
                conv.getId(), projectId, provider, contextSessionId);
        return conv;
    }

    /**
     * 发送消息给 Agent
     * 1. 获取/创建会话
     * 2. 调用 Adapter 提交消息
     * 3. 回写 context_session_id
     * 4. 返回 run_id 供轮询
     */
    @Transactional
    public SendMessageResult sendMessage(Long conversationId, String message, Long userId) {
        AgentConversation conv = getById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }
        if (!"active".equals(conv.getStatus())) {
            throw new IllegalStateException("会话已关闭: " + conversationId);
        }

        // 保存用户消息
        messageService.save(AgentMessage.builder()
                .conversationId(conversationId)
                .role("user")
                .content(message)
                .status("completed")
                .build());

        // 如果会话还没有标题，用第一条用户消息生成标题（截取前50字符）
        if (conv.getTitle() == null || conv.getTitle().isBlank()) {
            String title = message.length() > 50 ? message.substring(0, 50) + "…" : message;
            conv.setTitle(title);
        }
        // 更新会话的最后活跃时间
        conv.setUpdatedAt(OffsetDateTime.now());
        updateById(conv);

        // 调用 Adapter
        XyqConversationAdapter.SubmitResult submitResult = xyqAdapter.submit(message, conv.getContextSessionId());

        // 保存 Agent 消息占位（processing 状态）
        messageService.save(AgentMessage.builder()
                .conversationId(conversationId)
                .role("assistant")
                .runId(submitResult.getRunId())
                .status("processing")
                .build());

        // 回写 context_session_id（新创建的会话可能之前没有）
        if (conv.getContextSessionId() == null) {
            conv.setContextSessionId(submitResult.getThreadId());
            updateById(conv);
            updateProjectContext(conv.getProjectId(), conv.getProvider(), submitResult.getThreadId());
        }

        log.info("[AgentConv] Message sent: convId={}, threadId={}, runId={}",
                conversationId, submitResult.getThreadId(), submitResult.getRunId());

        return SendMessageResult.builder()
                .conversationId(conversationId)
                .threadId(submitResult.getThreadId())
                .runId(submitResult.getRunId())
                .status("processing")
                .build();
    }

    /**
     * 轮询会话结果
     * 1. 调用 Adapter 轮询
     * 2. 如果完成，提取媒体 → 存入 media_assets
     * 3. 返回结果
     */
    @Transactional
    public PollConversationResult pollConversation(Long conversationId, String runId) {
        AgentConversation conv = getById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }

        XyqConversationAdapter.PollResult pollResult = xyqAdapter.poll(conv.getContextSessionId(), runId);

        PollConversationResult.PollConversationResultBuilder resultBuilder = PollConversationResult.builder()
                .conversationId(conversationId)
                .threadId(conv.getContextSessionId())
                .runId(runId)
                .status(mapStatus(pollResult.getStatus()));

        if (pollResult.getStatus() == XyqConversationAdapter.PollStatus.FAILED) {
            resultBuilder.errorMsg(pollResult.getErrorMsg());
        }

        // 完成时提取媒体，存入资源库（防重复：按 runId 检查是否已保存过）
        if (pollResult.getStatus() == XyqConversationAdapter.PollStatus.COMPLETED && pollResult.getMediaItems() != null) {
            long existingCount = mediaAssetService.count(new LambdaQueryWrapper<MediaAsset>()
                    .eq(MediaAsset::getConversationId, conv.getId())
                    .eq(MediaAsset::getRunId, runId));
            if (existingCount == 0) {
                // 找到对应的 assistant 消息，获取其 ID
                AgentMessage assistantMsg = messageService.getByRunId(conv.getId(), runId);
                Long agentMessageId = assistantMsg != null ? assistantMsg.getId() : null;

                List<MediaAsset> savedAssets = new java.util.ArrayList<>();
                for (XyqConversationAdapter.MediaItem item : pollResult.getMediaItems()) {
                    MediaAsset asset = mediaAssetService.addAsset(
                            conv.getProjectId(),
                            conv.getUserId(),
                            conv.getId(),
                            "agent",
                            item.getMediaType(),
                            item.getUrl(),
                            item.getThumbnailUrl(),
                            item.getMetadata(),
                            runId,
                            agentMessageId
                    );
                    // 落地下载：异步下载到本地存储
                    try {
                        String localPath = mediaDownloadService.downloadToLocal(asset);
                        if (localPath != null) {
                            asset.setLocalPath(localPath);
                            mediaAssetService.updateById(asset);
                        }
                    } catch (Exception e) {
                        log.warn("[AgentConv] Failed to download media id={}: {}", asset.getId(), e.getMessage());
                    }
                    savedAssets.add(asset);
                }
                resultBuilder.mediaCount(savedAssets.size());
                log.info("[AgentConv] Saved {} media assets for conversation {}", savedAssets.size(), conversationId);
            } else {
                log.info("[AgentConv] Media assets already exist for conversation {}, skipping", conversationId);
                resultBuilder.mediaCount((int) existingCount);
            }

            // 更新 Agent 消息为 completed，附带文字内容
            String textContent = pollResult.getTextMessages() != null
                ? pollResult.getTextMessages().stream()
                    .filter(tm -> tm.getText() != null && !tm.getText().isBlank())
                    .map(XyqConversationAdapter.TextMessage::getText)
                    .collect(java.util.stream.Collectors.joining("\n\n"))
                : null;
            int mediaCount = pollResult.getMediaItems() != null ? pollResult.getMediaItems().size() : 0;
            updateAssistantMessage(conversationId, runId, "completed", null, mediaCount, textContent);
        }

        // 提取文字消息（无论什么状态都提取，这样 processing 时也能拿到中间文字）
        if (pollResult.getTextMessages() != null) {
            resultBuilder.textMessages(pollResult.getTextMessages().stream()
                    .map(tm -> TextMessageDTO.builder()
                            .messageId(tm.getMessageId())
                            .role(tm.getRole())
                            .text(tm.getText())
                            .subType(tm.getSubType())
                            .rawContent(tm.getRawContent())
                            .entryIndex(tm.getEntryIndex())
                            .build())
                    .collect(java.util.stream.Collectors.toList()));
        }

        // 失败时更新消息状态
        if (pollResult.getStatus() == XyqConversationAdapter.PollStatus.FAILED) {
            updateAssistantMessage(conversationId, runId, "failed", pollResult.getErrorMsg(), 0, null);
        }

        return resultBuilder.build();
    }

    /**
     * 获取项目的活跃会话
     */
    public AgentConversation getActiveConversation(Long projectId) {
        LambdaQueryWrapper<AgentConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentConversation::getProjectId, projectId)
               .eq(AgentConversation::getStatus, "active")
               .orderByDesc(AgentConversation::getCreatedAt)
               .last("LIMIT 1");
        return getOne(wrapper);
    }

    /**
     * 获取项目的所有会话
     */
    public List<AgentConversation> listByProjectId(Long projectId) {
        LambdaQueryWrapper<AgentConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentConversation::getProjectId, projectId)
               .orderByDesc(AgentConversation::getUpdatedAt);
        return list(wrapper);
    }

    /**
     * 获取用户的所有会话（按最后更新时间倒序）
     */
    public List<AgentConversation> listByUserId(Long userId) {
        LambdaQueryWrapper<AgentConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentConversation::getUserId, userId)
               .orderByDesc(AgentConversation::getUpdatedAt);
        return list(wrapper);
    }

    /**
     * 关闭会话
     */
    @Transactional
    public void closeConversation(Long conversationId) {
        AgentConversation conv = getById(conversationId);
        if (conv != null && "active".equals(conv.getStatus())) {
            conv.setStatus("closed");
            updateById(conv);
            log.info("[AgentConv] Closed conversation id={}", conversationId);
        }
    }

    /**
     * 更新会话的 context_session_id
     */
    @Transactional
    public void updateContextSessionId(Long conversationId, String contextSessionId) {
        AgentConversation conv = getById(conversationId);
        if (conv != null) {
            conv.setContextSessionId(contextSessionId);
            updateById(conv);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 更新 Agent 消息状态
     */
    private void updateAssistantMessage(Long conversationId, String runId, String status, String errorMsg, int mediaCount, String textContent) {
        AgentMessage msg = messageService.getByRunId(conversationId, runId);
        if (msg != null) {
            msg.setStatus(status);
            msg.setErrorMsg(errorMsg);
            msg.setMediaCount(mediaCount);
            if (textContent != null && !textContent.isBlank()) {
                msg.setText(textContent);
            }
            messageService.updateById(msg);
        }
    }

    private void updateProjectContext(Long projectId, String provider, String contextSessionId) {
        Project project = projectService.getById(projectId);
        if (project != null) {
            project.setModelProvider(provider);
            project.setContextSessionId(contextSessionId);
            projectService.updateById(project);
            log.info("[AgentConv] Updated project id={} contextSessionId={}", projectId, contextSessionId);
        }
    }

    private String mapStatus(XyqConversationAdapter.PollStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case PROCESSING -> "processing";
            case COMPLETED -> "completed";
            case FAILED -> "failed";
        };
    }

    // ========== DTO ==========

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SendMessageResult {
        private Long conversationId;
        private String threadId;
        private String runId;
        private String status;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PollConversationResult {
        private Long conversationId;
        private String threadId;
        private String runId;
        private String status;
        private Integer mediaCount;
        private String errorMsg;
        private List<TextMessageDTO> textMessages;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TextMessageDTO {
        private String messageId;
        private String role;
        private String text;
        private String subType;
        private String rawContent;
        private int entryIndex;
    }
}