package com.luciano.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.ModelProvider;
import com.luciano.service.ModelProviderService;
import com.luciano.spi.ImageGenerator;
import com.luciano.spi.request.ImageGenerateRequest;
import com.luciano.spi.response.ImageGenerateResult;
import com.luciano.spi.response.ImagePollResult;
import com.luciano.spi.response.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 小云雀图片生成适配器
 * 复用 Agent API，提示词明确指定"生成一张图片"
 */
@Component("xyqImage")
@Slf4j
public class XyqImageGenerator implements ImageGenerator {

    private static final String SUBMIT_PATH = "/skill/submit_run";
    private static final String THREAD_PATH = "/skill/get_thread";

    private final ModelHttpClient httpClient;
    private final ModelProviderService providerService;
    private final ObjectMapper objectMapper;

    public XyqImageGenerator(ModelHttpClient httpClient,
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
    public ImageGenerateResult generate(ImageGenerateRequest request) {
        ModelProvider provider = getProviderEntity();
        String baseUrl = provider.getBaseUrl();
        String accessToken = provider.getApiKey();

        // 构建提示词 — 明确说明生成图片，避免被理解成视频
        String prompt = "请生成一张图片。" + request.getPrompt();
        if (request.getNegativePrompt() != null) {
            prompt += "。不要包含：" + request.getNegativePrompt();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("message", prompt);
        if (request.getExtra() != null && request.getExtra().containsKey("thread_id")) {
            body.put("thread_id", request.getExtra().get("thread_id"));
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

            log.info("[XyqImage] submit response: {}", response);

            JsonNode dataNode = response.has("data") ? response.get("data") : response;
            // 小云雀返回格式: data.run.thread_id / data.run.run_id
            JsonNode runNode = dataNode.has("run") ? dataNode.get("run") : dataNode;
            String threadId = runNode.has("thread_id") ? runNode.get("thread_id").asText() : null;
            String runId = runNode.has("run_id") ? runNode.get("run_id").asText() : null;

            if (threadId == null || runId == null) {
                log.error("[XyqImage] Missing thread_id or run_id in response. dataNode: {}", dataNode);
                throw new RuntimeException("小云雀返回数据缺少 thread_id 或 run_id");
            }

            // 用 threadId 作为 taskId（后续轮询用 threadId）
            return ImageGenerateResult.builder()
                    .taskId(threadId + ":" + runId)
                    .provider("xyq")
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("Xyq image submit failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("小云雀图片提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ImagePollResult poll(String taskId) {
        // taskId 格式: threadId:runId
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

            log.info("[XyqImage] poll response keys: {}", response.has("data") ? response.get("data").fieldNames() : "no-data");

            JsonNode dataNode = response.has("data") ? response.get("data") : response;
            // get_thread 返回格式: data.thread.run_list[0]
            JsonNode threadNode = dataNode.has("thread") ? dataNode.get("thread") : dataNode;
            JsonNode runList = threadNode.has("run_list") ? threadNode.get("run_list") : null;
            JsonNode runNode = (runList != null && runList.isArray() && runList.size() > 0)
                    ? runList.get(0) : threadNode;

            int runState = runNode.has("state") ? runNode.get("state").asInt()
                    : (runNode.has("run_state") ? runNode.get("run_state").asInt() : -1);

            // 检查 entry_list 中是否有 biz/error
            String errorMsg = checkEntryErrors(runNode);

            TaskStatus status;
            String outputUrl = null;

            if (errorMsg != null) {
                // 小云雀可能 state=3 但内容是 error（如积分不足）
                status = TaskStatus.FAILED;
            } else if (runState == 3) {
                status = TaskStatus.COMPLETED;
                outputUrl = extractImageUrlFromRun(runNode);
            } else if (runState == 4) {
                status = TaskStatus.FAILED;
            } else {
                status = TaskStatus.PROCESSING;
            }

            return ImagePollResult.builder()
                    .status(status)
                    .outputUrl(outputUrl)
                    .errorMsg(status == TaskStatus.FAILED ? (errorMsg != null ? errorMsg : "图片生成失败") : null)
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("Xyq image poll failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            return ImagePollResult.builder()
                    .status(TaskStatus.FAILED)
                    .errorMsg("小云雀图片轮询失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 从 get_thread 响应的 run 中提取图片 URL
     * 遍历 entry_list，找 type=2 的 artifact，其中 biz/x_data_image 包含图片 URL
     */
    private String extractImageUrlFromRun(JsonNode runNode) {
        JsonNode entryList = runNode.has("entry_list") ? runNode.get("entry_list") : null;
        if (entryList == null || !entryList.isArray()) return null;

        for (JsonNode entry : entryList) {
            int type = entry.has("type") ? entry.get("type").asInt() : 0;
            if (type == 2) {
                // artifact 类型
                JsonNode artifact = entry.get("artifact");
                if (artifact != null && artifact.has("content")) {
                    for (JsonNode content : artifact.get("content")) {
                        String subType = content.has("sub_type") ? content.get("sub_type").asText() : "";
                        if ("biz/x_data_image".equals(subType)) {
                            try {
                                String dataStr = content.get("data").asText();
                                JsonNode imageData = objectMapper.readTree(dataStr);
                                // 图片 URL 在 image.url
                                if (imageData.has("image")) {
                                    JsonNode image = imageData.get("image");
                                    if (image.has("url")) {
                                        return image.get("url").asText();
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("[XyqImage] Failed to parse x_data_image: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 旧格式：从 biz 或 artifacts 中提取图片 URL（兼容）
     */
    private String extractImageUrl(JsonNode dataNode) {
        // biz.x_data_image 格式
        if (dataNode.has("biz")) {
            JsonNode biz = dataNode.get("biz");
            if (biz.has("x_data_image")) {
                JsonNode imageData = biz.get("x_data_image");
                if (imageData.isArray() && imageData.size() > 0) {
                    return imageData.get(0).has("url") ? imageData.get(0).get("url").asText() : null;
                }
                if (imageData.isObject() && imageData.has("url")) {
                    return imageData.get("url").asText();
                }
            }
        }
        // artifacts 格式
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
     * 小云雀可能返回 state=3 但 entry_list 中包含错误（如积分不足）
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