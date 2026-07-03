package com.luciano.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.ModelProvider;
import com.luciano.service.ModelProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 小云雀会话适配器（Agent 中转站模式）
 * 
 * 核心职责：
 * 1. 创建/复用会话（thread）
 * 2. 转发用户消息
 * 3. 轮询结果
 * 4. 从结果中提取所有媒体 URL（不区分角色/场景/道具）
 * 
 * 与专业模式 Adapter 的区别：
 * - 专业模式：按步骤生成（角色图、场景图、首帧、视频各一个任务）
 * - 中转站模式：用户发一句话，Agent 自己决定生成什么，平台只提取媒体文件
 */
@Component
@Slf4j
public class XyqConversationAdapter {

    private static final String SUBMIT_PATH = "/skill/submit_run";
    private static final String THREAD_PATH = "/skill/get_thread";

    private final ModelHttpClient httpClient;
    private final ModelProviderService providerService;
    private final ObjectMapper objectMapper;

    public XyqConversationAdapter(ModelHttpClient httpClient,
                                  ModelProviderService providerService,
                                  ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.providerService = providerService;
        this.objectMapper = objectMapper;
    }

    // ========== 公开方法 ==========

    /**
     * 提交消息到小云雀 Agent
     * @param message 用户消息
     * @param threadId 会话 ID（null 则创建新会话）
     * @return 提交结果
     */
    public SubmitResult submit(String message, String threadId) {
        ModelProvider provider = getProviderEntity();
        String baseUrl = provider.getBaseUrl();
        String accessToken = provider.getApiKey();

        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        if (threadId != null && !threadId.isBlank()) {
            body.put("thread_id", threadId);
        }

        ModelHttpClient.Headers headers = ModelHttpClient.Headers.of(
                "Authorization", "Bearer " + accessToken
        );

        try {
            JsonNode response = httpClient.post(
                    baseUrl + SUBMIT_PATH,
                    headers,
                    body,
                    JsonNode.class,
                    2
            );

            JsonNode dataNode = response.has("data") ? response.get("data") : response;
            JsonNode runNode = dataNode.has("run") ? dataNode.get("run") : dataNode;
            String returnedThreadId = runNode.has("thread_id") ? runNode.get("thread_id").asText() : null;
            String runId = runNode.has("run_id") ? runNode.get("run_id").asText() : null;

            if (returnedThreadId == null || runId == null) {
                throw new RuntimeException("小云雀返回数据缺少 thread_id 或 run_id");
            }

            log.info("[XyqConv] Submit success: threadId={}, runId={}", returnedThreadId, runId);

            return SubmitResult.builder()
                    .threadId(returnedThreadId)
                    .runId(runId)
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("[XyqConv] Submit failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("小云雀会话提交失败: " + e.getMessage(), e);
        }
    }

    /**
     * 轮询会话结果
     * @param threadId 会话 ID
     * @param runId 运行 ID
     * @return 轮询结果
     */
    public PollResult poll(String threadId, String runId) {
        ModelProvider provider = getProviderEntity();
        String baseUrl = provider.getBaseUrl();
        String accessToken = provider.getApiKey();

        Map<String, Object> body = new HashMap<>();
        body.put("thread_id", threadId);
        if (runId != null) {
            body.put("run_id", runId);
        }

        ModelHttpClient.Headers headers = ModelHttpClient.Headers.of(
                "Authorization", "Bearer " + accessToken
        );

        try {
            JsonNode response = httpClient.post(
                    baseUrl + THREAD_PATH,
                    headers,
                    body,
                    JsonNode.class
            );

            // 解析状态
            JsonNode dataNode = response.has("data") ? response.get("data") : response;
            JsonNode threadNode = dataNode.has("thread") ? dataNode.get("thread") : dataNode;
            JsonNode runList = threadNode.has("run_list") ? threadNode.get("run_list") : null;
            JsonNode runNode = (runList != null && runList.isArray() && runList.size() > 0)
                    ? runList.get(0) : threadNode;
            int runState = runNode.has("state") ? runNode.get("state").asInt()
                    : (runNode.has("run_state") ? runNode.get("run_state").asInt() : -1);

            // 检查 biz/error
            String errorMsg = checkEntryErrors(runNode);

            // state: 1=pending, 2=running, 3=completed, 4=failed
            PollStatus status;
            if (errorMsg != null) {
                status = PollStatus.FAILED;
            } else if (runState == 3) {
                status = PollStatus.COMPLETED;
            } else if (runState == 4) {
                status = PollStatus.FAILED;
            } else if (runState == 1 || runState == 2) {
                status = PollStatus.PROCESSING;
            } else {
                status = PollStatus.PENDING;
            }

            // 提取媒体和文字消息
            List<MediaItem> mediaItems = Collections.emptyList();
            List<TextMessage> textMessages = Collections.emptyList();
            if (status == PollStatus.COMPLETED || status == PollStatus.PROCESSING) {
                mediaItems = extractAllMedia(runNode);
                textMessages = extractTextMessages(runNode);
            }

            return PollResult.builder()
                    .status(status)
                    .threadId(threadId)
                    .runId(runId)
                    .mediaItems(mediaItems)
                    .textMessages(textMessages)
                    .errorMsg(status == PollStatus.FAILED ? (errorMsg != null ? errorMsg : "Agent 执行失败") : null)
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("[XyqConv] Poll failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            return PollResult.builder()
                    .status(PollStatus.FAILED)
                    .threadId(threadId)
                    .runId(runId)
                    .errorMsg("小云雀会话轮询失败: " + e.getMessage())
                    .build();
        }
    }

    // ========== 内部方法 ==========

    /**
     * 从 Agent 响应中提取所有媒体文件
     * 不区分角色/场景/道具，全部提取为 MediaItem 列表
     */
    List<MediaItem> extractAllMedia(JsonNode runNode) {
        List<MediaItem> items = new ArrayList<>();
        JsonNode entryList = runNode.has("entry_list") ? runNode.get("entry_list") : null;
        if (entryList == null || !entryList.isArray()) return items;

        for (JsonNode entry : entryList) {
            int type = entry.has("type") ? entry.get("type").asInt() : 0;
            if (type != 2) continue;  // 只处理 artifact

            JsonNode artifact = entry.get("artifact");
            if (artifact == null || !artifact.has("content")) continue;

            for (JsonNode content : artifact.get("content")) {
                String subType = content.has("sub_type") ? content.get("sub_type").asText() : "";

                if ("biz/x_data_image".equals(subType)) {
                    String url = parseImageUrl(content);
                    if (url != null) {
                        items.add(MediaItem.builder()
                                .mediaType("image")
                                .url(url)
                                .source("agent")
                                .build());
                    }
                } else if ("biz/x_data_video".equals(subType)) {
                    String url = parseVideoUrl(content);
                    if (url != null) {
                        items.add(MediaItem.builder()
                                .mediaType("video")
                                .url(url)
                                .source("agent")
                                .build());
                    }
                }
            }
        }

        log.info("[XyqConv] Extracted {} media items from response", items.size());
        return items;
    }

    private String parseImageUrl(JsonNode content) {
        try {
            String dataStr = content.get("data").asText();
            JsonNode imageData = objectMapper.readTree(dataStr);
            if (imageData.has("image")) {
                JsonNode image = imageData.get("image");
                if (image.has("url")) {
                    return image.get("url").asText();
                }
            }
        } catch (Exception e) {
            log.warn("[XyqConv] Failed to parse x_data_image: {}", e.getMessage());
        }
        return null;
    }

    private String parseVideoUrl(JsonNode content) {
        try {
            String dataStr = content.get("data").asText();
            JsonNode videoData = objectMapper.readTree(dataStr);
            if (videoData.has("video")) {
                JsonNode video = videoData.get("video");
                if (video.has("url")) {
                    return video.get("url").asText();
                }
            }
        } catch (Exception e) {
            log.warn("[XyqConv] Failed to parse x_data_video: {}", e.getMessage());
        }
        return null;
    }

/**
     * 从 Agent 响应中提取文字消息（分析、提问、表单等）
     */
    List<TextMessage> extractTextMessages(JsonNode runNode) {
        List<TextMessage> messages = new ArrayList<>();
        JsonNode entryList = runNode.has("entry_list") ? runNode.get("entry_list") : null;
        if (entryList == null || !entryList.isArray()) return messages;

        for (int entryIdx = 0; entryIdx < entryList.size(); entryIdx++) {
            JsonNode entry = entryList.get(entryIdx);
            // message 类型的 entry（type=1）
            JsonNode msgNode = entry.get("message");
            if (msgNode != null) {
                String role = msgNode.has("role") ? msgNode.get("role").asText() : "";
                if (!"assistant".equals(role)) continue;  // 只取 assistant 的文字

                JsonNode contentList = msgNode.has("content") ? msgNode.get("content") : null;
                if (contentList != null && contentList.isArray()) {
                    for (JsonNode content : contentList) {
                        String subType = content.has("sub_type") ? content.get("sub_type").asText() : "";
                        String messageId = msgNode.has("message_id") ? msgNode.get("message_id").asText() : "";

                        // 文字消息（sub_type 为 text、markdown 或空字符串）
                        if ("text".equals(subType) || "markdown".equals(subType) || subType.isEmpty()) {
                            String text = content.has("data") ? content.get("data").asText() : "";
                            if (!text.isEmpty()) {
                                messages.add(TextMessage.builder()
                                        .messageId(messageId)
                                        .role("assistant")
                                        .text(text)
                                        .subType(subType)
                                        .entryIndex(entryIdx)
                                        .build());
                            }
                        }
                        // 结构化内容（表单、工具调用等）
                        else if (subType.startsWith("biz/") || "tool_call_req".equals(subType)) {
                            // tool_call_req 通常包含 loadingText，提取显示给用户
                            String displayText = "";
                            String data = content.has("data") ? content.get("data").asText() : "";
                            try {
                                JsonNode dataJson = objectMapper.readTree(data);
                                if (dataJson.has("extra")) {
                                    JsonNode extra = objectMapper.readTree(dataJson.get("extra").asText());
                                    displayText = extra.has("loadingText") ? extra.get("loadingText").asText() : "";
                                }
                                if (displayText.isEmpty() && dataJson.has("display_name")) {
                                    displayText = dataJson.get("display_name").asText();
                                }
                            } catch (Exception e) { /* 忽略解析错误 */ }
                            
                            messages.add(TextMessage.builder()
                                    .messageId(messageId)
                                    .role("assistant")
                                    .text(displayText)
                                    .subType(subType)
                                    .rawContent(data)
                                    .entryIndex(entryIdx)
                                    .build());
                        }
                    }
                }
            }
        }

        log.info("[XyqConv] Extracted {} text messages from response", messages.size());
        return messages;
    }

    private String checkEntryErrors(JsonNode runNode) {
        JsonNode entryList = runNode.has("entry_list") ? runNode.get("entry_list") : null;
        if (entryList == null || !entryList.isArray()) return null;

        for (JsonNode entry : entryList) {
            JsonNode msg = entry.get("message");
            if (msg != null && msg.has("content")) {
                for (JsonNode content : msg.get("content")) {
                    String subType = content.has("sub_type") ? content.get("sub_type").asText() : "";
                    if ("biz/error".equals(subType)) {
                        try {
                            String dataStr = content.get("data").asText();
                            JsonNode errorData = objectMapper.readTree(dataStr);
                            return errorData.has("message") ? errorData.get("message").asText() : "小云雀执行出错";
                        } catch (Exception e) {
                            return "小云雀执行出错";
                        }
                    }
                }
            }
        }
        return null;
    }

    private ModelProvider getProviderEntity() {
        ModelProvider provider = providerService.getByName("xyq");
        if (provider == null) {
            throw new IllegalStateException("未配置小云雀模型服务商");
        }
        return provider;
    }

    // ========== DTO ==========

    public enum PollStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SubmitResult {
        private String threadId;
        private String runId;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PollResult {
        private PollStatus status;
        private String threadId;
        private String runId;
        private List<MediaItem> mediaItems;
        private List<TextMessage> textMessages;
        private String errorMsg;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TextMessage {
        private String messageId;
        private String role;       // assistant
        private String text;      // 文字内容
        private String subType;   // text / markdown / biz/xxx / tool_call_req / ""(empty)
        private String rawContent; // 原始 content JSON
        private int entryIndex;  // entry 在 entry_list 中的索引，用于前端唯一标识
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class MediaItem {
        private String mediaType;   // image / video
        private String url;
        private String source;      // agent
        private String thumbnailUrl;
        private String metadata;
    }
}