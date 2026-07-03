package com.luciano.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.ModelProvider;
import com.luciano.service.ModelConfigService;
import com.luciano.service.ModelProviderService;
import com.luciano.spi.AgentGenerator;
import com.luciano.spi.request.AgentSubmitRequest;
import com.luciano.spi.response.AgentPollResult;
import com.luciano.spi.response.AgentSubmitResult;
import com.luciano.spi.response.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 小云雀 Agent 适配器
 * 对接小云雀 Agent 会话式 API
 * 
 * API 流程：
 * 1. submit_run → 提交任务，获取 thread_id + run_id
 * 2. get_thread → 轮询任务状态
 * 3. 解析产物（图片/视频 URL）
 */
@Component("xyqAgent")
@Slf4j
public class XyqAgentGenerator implements AgentGenerator {

    private static final String SUBMIT_PATH = "/skill/submit_run";
    private static final String THREAD_PATH = "/skill/get_thread";
    private static final String UPLOAD_PATH = "/skill/upload_file";

    private final ModelHttpClient httpClient;
    private final ModelProviderService providerService;
    private final ObjectMapper objectMapper;

    public XyqAgentGenerator(ModelHttpClient httpClient,
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
    public AgentSubmitResult submit(AgentSubmitRequest request) {
        ModelProvider provider = getProviderEntity();
        String baseUrl = provider.getBaseUrl();
        String accessToken = provider.getApiKey();

        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("message", request.getPrompt());
        if (request.getThreadId() != null && !request.getThreadId().isBlank()) {
            body.put("thread_id", request.getThreadId());
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
                    2  // 最多重试 2 次
            );

            JsonNode dataNode = response.has("data") ? response.get("data") : response;
            JsonNode runNode = dataNode.has("run") ? dataNode.get("run") : dataNode;
            String threadId = runNode.has("thread_id") ? runNode.get("thread_id").asText() : null;
            String runId = runNode.has("run_id") ? runNode.get("run_id").asText() : null;

            if (threadId == null || runId == null) {
                throw new RuntimeException("小云雀返回数据缺少 thread_id 或 run_id");
            }

            return AgentSubmitResult.builder()
                    .threadId(threadId)
                    .runId(runId)
                    .provider("xyq")
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("Xyq submit failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("小云雀 Agent 提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AgentPollResult poll(String threadId, String runId) {
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
            
            // 检查 entry_list 中是否有 biz/error
            String errorMsg = checkEntryErrors(runNode);

            // run_state: 1=pending, 2=running, 3=completed, 4=failed
            TaskStatus status;
            if (errorMsg != null) {
                status = TaskStatus.FAILED;
            } else if (runState == 3) {
                status = TaskStatus.COMPLETED;
            } else if (runState == 4) {
                status = TaskStatus.FAILED;
            } else if (runState == 1 || runState == 2) {
                status = TaskStatus.PROCESSING;
            } else {
                status = TaskStatus.PENDING;
            }

            // 解析产物 — 从 entry_list 中提取
            Map<String, Object> artifacts = new HashMap<>();
            if (status == TaskStatus.COMPLETED && runNode.has("entry_list")) {
                JsonNode entryList = runNode.get("entry_list");
                if (entryList.isArray()) {
                    for (JsonNode entry : entryList) {
                        int type = entry.has("type") ? entry.get("type").asInt() : 0;
                        if (type == 2 && entry.has("artifact")) {
                            JsonNode artifact = entry.get("artifact");
                            artifacts.put("artifact_" + artifact.path("artifact_id").asText("unknown"),
                                    objectMapper.convertValue(artifact, Map.class));
                        }
                    }
                }
            }

            return AgentPollResult.builder()
                    .status(status)
                    .artifacts(artifacts.isEmpty() ? null : artifacts)
                    .errorMsg(status == TaskStatus.FAILED ? (errorMsg != null ? errorMsg : "Agent 执行失败") : null)
                    .build();

        } catch (ModelHttpClient.ModelHttpException e) {
            log.error("Xyq poll failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
            return AgentPollResult.builder()
                    .status(TaskStatus.FAILED)
                    .errorMsg("小云雀 Agent 轮询失败: " + e.getMessage())
                    .build();
        }
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
}