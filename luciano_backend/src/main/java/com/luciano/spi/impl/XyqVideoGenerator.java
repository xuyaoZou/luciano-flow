package com.luciano.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.ModelProvider;
import com.luciano.service.ModelProviderService;
import com.luciano.spi.VideoGenerator;
import com.luciano.spi.request.VideoGenerateRequest;
import com.luciano.spi.response.VideoGenerateResult;
import com.luciano.spi.response.VideoPollResult;
import com.luciano.spi.response.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 小云雀视频生成适配器
 * 复用 Agent API，提示词说明生成视频
 */
@Component("xyqVideo")
@Slf4j
public class XyqVideoGenerator implements VideoGenerator {

    private static final String SUBMIT_PATH = "/skill/submit_run";
    private static final String THREAD_PATH = "/skill/get_thread";

    private final ModelHttpClient httpClient;
    private final ModelProviderService providerService;
    private final ObjectMapper objectMapper;

    public XyqVideoGenerator(ModelHttpClient httpClient,
                             ModelProviderService providerService,
                             ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.providerService = providerService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProvider() {
        return "xyq";
    }

    @Override
    public VideoGenerateResult generate(VideoGenerateRequest request) {
        ModelProvider provider = getProviderEntity();
        String baseUrl = provider.getBaseUrl();
        String accessToken = provider.getApiKey();

        Map<String, Object> body = new HashMap<>();
        body.put("message", request.getPrompt());
        if (request.getExtra() != null && request.getExtra().containsKey("thread_id")) {
            body.put("thread_id", request.getExtra().get("thread_id"));
        }
        if (request.getFirstFrameUrl() != null) {
            body.put("first_frame_url", request.getFirstFrameUrl());
        }
        if (request.getLastFrameUrl() != null) {
            body.put("last_frame_url", request.getLastFrameUrl());
        }
        if (request.getReferenceUrls() != null && !request.getReferenceUrls().isEmpty()) {
            body.put("reference_images", request.getReferenceUrls());
        }
        if (request.getExtra() != null) {
            body.putAll(request.getExtra());
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
            String threadId = runNode.has("thread_id") ? runNode.get("thread_id").asText() : null;
            String runId = runNode.has("run_id") ? runNode.get("run_id").asText() : null;

            if (threadId == null || runId == null) {
                throw new RuntimeException("小云雀返回数据缺少 thread_id 或 run_id");
            }

            return VideoGenerateResult.builder()
                    .taskId(threadId + ":" + runId)
                    .provider("xyq")
                    .threadId(threadId)
                    .runId(runId)
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("Xyq video submit failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("小云雀视频提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoPollResult poll(String taskId) {
        String[] parts = taskId.split(":");
        String threadId = parts[0];
        String runId = parts.length > 1 ? parts[1] : null;

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

            JsonNode dataNode = response.has("data") ? response.get("data") : response;
            JsonNode threadNode = dataNode.has("thread") ? dataNode.get("thread") : dataNode;
            JsonNode runList = threadNode.has("run_list") ? threadNode.get("run_list") : null;
            JsonNode runNode = (runList != null && runList.isArray() && runList.size() > 0)
                    ? runList.get(0) : threadNode;
            int runState = runNode.has("state") ? runNode.get("state").asInt()
                    : (runNode.has("run_state") ? runNode.get("run_state").asInt() : -1);

            String errorMsg = checkEntryErrors(runNode);

            TaskStatus status;
            String outputUrl = null;

            if (errorMsg != null) {
                status = TaskStatus.FAILED;
            } else if (runState == 3) {
                status = TaskStatus.COMPLETED;
                outputUrl = extractVideoUrlFromRun(runNode);
            } else if (runState == 4) {
                status = TaskStatus.FAILED;
            } else {
                status = TaskStatus.PROCESSING;
            }

            return VideoPollResult.builder()
                    .status(status)
                    .outputUrl(outputUrl)
                    .errorMsg(status == TaskStatus.FAILED ? (errorMsg != null ? errorMsg : "视频生成失败") : null)
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("Xyq video poll failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            return VideoPollResult.builder()
                    .status(TaskStatus.FAILED)
                    .errorMsg("小云雀视频轮询失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public void cancel(String taskId) {
        log.warn("Xyq cancel not supported yet for taskId: {}", taskId);
        // 小云雀暂不支持取消任务
    }

    /**
     * 从 get_thread 响应的 run 中提取视频 URL
     * 遍历 entry_list，找 type=2 的 artifact，其中 biz/x_data_video 包含视频 URL
     */
    private String extractVideoUrlFromRun(JsonNode runNode) {
        JsonNode entryList = runNode.has("entry_list") ? runNode.get("entry_list") : null;
        if (entryList == null || !entryList.isArray()) return null;

        for (JsonNode entry : entryList) {
            int type = entry.has("type") ? entry.get("type").asInt() : 0;
            if (type == 2) {
                JsonNode artifact = entry.get("artifact");
                if (artifact != null && artifact.has("content")) {
                    for (JsonNode content : artifact.get("content")) {
                        String subType = content.has("sub_type") ? content.get("sub_type").asText() : "";
                        if ("biz/x_data_video".equals(subType)) {
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
                                log.warn("[XyqVideo] Failed to parse x_data_video: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractVideoUrl(JsonNode dataNode) {
        // 从 biz 中提取视频 URL
        if (dataNode.has("biz")) {
            JsonNode biz = dataNode.get("biz");
            if (biz.has("x_data_video")) {
                JsonNode videoData = biz.get("x_data_video");
                if (videoData.isArray() && videoData.size() > 0) {
                    return videoData.get(0).has("url") ? videoData.get(0).get("url").asText() : null;
                }
                if (videoData.isObject() && videoData.has("url")) {
                    return videoData.get("url").asText();
                }
            }
        }
        if (dataNode.has("artifacts")) {
            JsonNode artifacts = dataNode.get("artifacts");
            if (artifacts.isArray() && artifacts.size() > 0) {
                return artifacts.get(0).has("url") ? artifacts.get(0).get("url").asText() : null;
            }
        }
        return null;
    }

    /**
     * 检查 entry_list 中是否有 biz/error
     */
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
}