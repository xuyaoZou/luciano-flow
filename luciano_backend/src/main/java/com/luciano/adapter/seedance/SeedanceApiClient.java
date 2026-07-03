package com.luciano.adapter.seedance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.ModelProvider;
import com.luciano.service.ModelProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Seedance (火山方舟) API HTTP 客户端
 * <p>
 * 认证方式：Bearer Token（火山方舟 API Key）
 * 国内版走火山方舟平台（ark.cn-beijing.volces.com），不是视觉智能平台。
 * 与 Kling 的 JWT 签名不同，方舟使用简单的 API Key 认证。
 */
@Component
@Slf4j
public class SeedanceApiClient {

    private final ModelHttpClient httpClient;
    private final ModelProviderService providerService;
    private final ObjectMapper objectMapper;

    public SeedanceApiClient(ModelHttpClient httpClient,
                             ModelProviderService providerService,
                             ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.providerService = providerService;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 Seedance 服务商配置
     */
    public ModelProvider getProvider() {
        ModelProvider provider = providerService.getByName("seedance");
        if (provider == null) {
            throw new IllegalStateException("未配置 Seedance 模型服务商，请在 model_providers 表添加 name='seedance' 记录");
        }
        return provider;
    }

    /**
     * 获取 API 基础 URL
     * 火山方舟默认：https://ark.cn-beijing.volces.com
     */
    public String getBaseUrl() {
        ModelProvider provider = getProvider();
        return provider.getBaseUrl() != null ? provider.getBaseUrl() : "https://ark.cn-beijing.volces.com";
    }

    /**
     * 构建认证请求头
     * 火山方舟使用 Bearer Token（API Key）
     */
    public ModelHttpClient.Headers authHeaders() {
        ModelProvider provider = getProvider();
        String apiKey = provider.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Seedance API Key 未配置");
        }
        return ModelHttpClient.Headers.of("Authorization", "Bearer " + apiKey);
    }

    /**
     * POST 请求（创建任务）
     */
    public <T> T post(String path, Object body, Class<T> responseType) {
        return httpClient.post(getBaseUrl() + path, authHeaders(), body, responseType);
    }

    /**
     * POST 请求（带重试）
     */
    public <T> T post(String path, Object body, Class<T> responseType, int maxRetries) {
        return httpClient.post(getBaseUrl() + path, authHeaders(), body, responseType, maxRetries);
    }

    /**
     * GET 请求（查询任务状态）
     */
    public <T> T get(String path, Class<T> responseType) {
        return httpClient.get(getBaseUrl() + path, authHeaders(), responseType);
    }

    /**
     * 解析 Seedance API 错误响应
     */
    public String parseErrorMessage(Object response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            if (map.containsKey("error")) {
                Object error = map.get("error");
                if (error instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorMap = (Map<String, Object>) error;
                    return String.format("Seedance API error: code=%s, message=%s",
                            errorMap.get("code"), errorMap.get("message"));
                }
            }
            return json;
        } catch (Exception e) {
            return String.valueOf(response);
        }
    }
}