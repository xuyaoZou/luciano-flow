package com.luciano.adapter.kling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.config.ModelHttpClient;
import com.luciano.entity.ModelProvider;
import com.luciano.service.ModelProviderService;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Kling API HTTP 客户端
 * 处理 JWT 签名认证、请求构建、错误映射。
 *
 * 认证方式：使用 AK/SK 生成 JWT Token
 * - Header: alg=HS256, typ=JWT
 * - Payload: iss=AK, exp=当前时间+1800秒, nbf=当前时间-5秒
 * - Signature: HMAC-SHA256(SK)
 */
@Component
@Slf4j
public class KlingApiClient {

    private final ModelHttpClient httpClient;
    private final ModelProviderService providerService;
    private final ObjectMapper objectMapper;

    /** JWT Token 缓存（避免每次请求都生成） */
    private volatile String cachedToken;
    private volatile long tokenExpiresAt = 0;

    public KlingApiClient(ModelHttpClient httpClient,
                          ModelProviderService providerService,
                          ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.providerService = providerService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成 Kling JWT Token
     * 文档：https://klingai.kuaishou.com/docs/api/auth
     */
    public synchronized String getToken() {
        // 缓存有效期内的 Token（提前 60 秒刷新）
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt - 60000) {
            return cachedToken;
        }

        ModelProvider provider = getProvider();
        String ak = provider.getApiKey();        // access_key
        String sk = provider.getApiSecret();     // secret_key

        if (ak == null || sk == null) {
            throw new IllegalStateException("Kling AK/SK 未配置，请在 model_providers 表配置 api_key 和 api_secret");
        }

        long now = System.currentTimeMillis();
        byte[] skBytes = sk.getBytes(StandardCharsets.UTF_8);
        javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(skBytes, "HmacSHA256");

        String token = Jwts.builder()
                .header().add("alg", "HS256").add("typ", "JWT").and()
                .issuer(ak)
                .issuedAt(new Date(now - 5000))   // iat
                .notBefore(new Date(now - 5000))  // nbf: 5秒前生效
                .expiration(new Date(now + 1800000)) // 30分钟过期
                .signWith(key)
                .compact();

        this.cachedToken = token;
        this.tokenExpiresAt = now + 1800000;

        log.info("[KlingApiClient] JWT Token generated, expires at {}", new Date(tokenExpiresAt));
        return token;
    }

    /**
     * 生成 Kling JWT Token（测试用，直接传入 AK/SK）
     */
    public String generateToken(String accessKey, String secretKey) {
        long now = System.currentTimeMillis();
        byte[] skBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new javax.crypto.spec.SecretKeySpec(skBytes, "HmacSHA256");

        return Jwts.builder()
                .header().add("alg", "HS256").add("typ", "JWT").and()
                .issuer(accessKey)
                .issuedAt(new Date(now - 5000))
                .notBefore(new Date(now - 5000))
                .expiration(new Date(now + 1800000))
                .signWith(key)
                .compact();
    }

    /**
     * 获取 Kling 服务商配置
     */
    public ModelProvider getProvider() {
        ModelProvider provider = providerService.getByName("kling");
        if (provider == null) {
            throw new IllegalStateException("未配置 Kling 模型服务商，请在 model_providers 表添加 name='kling' 记录");
        }
        return provider;
    }

    /**
     * 获取 API 基础 URL
     */
    public String getBaseUrl() {
        ModelProvider provider = getProvider();
        return provider.getBaseUrl() != null ? provider.getBaseUrl() : KlingConstants.BASE_URL_CN;
    }

    /**
     * 构建认证请求头
     */
    public ModelHttpClient.Headers authHeaders() {
        return ModelHttpClient.Headers.of("Authorization", "Bearer " + getToken());
    }

    /**
     * POST 请求（带认证）
     */
    public <T> T post(String path, Object body, Class<T> responseType) {
        return httpClient.post(getBaseUrl() + path, authHeaders(), body, responseType);
    }

    /**
     * POST 请求（带认证 + 重试）
     */
    public <T> T post(String path, Object body, Class<T> responseType, int maxRetries) {
        return httpClient.post(getBaseUrl() + path, authHeaders(), body, responseType, maxRetries);
    }

    /**
     * GET 请求（带认证）
     */
    public <T> T get(String path, Class<T> responseType) {
        return httpClient.get(getBaseUrl() + path, authHeaders(), responseType);
    }

    /**
     * 解析 Kling API 错误响应
     */
    public String parseErrorMessage(Object response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            if (map.containsKey("code")) {
                return String.format("Kling API error: code=%s, message=%s",
                        map.get("code"), map.get("message"));
            }
            return json;
        } catch (Exception e) {
            return String.valueOf(response);
        }
    }

    // ==================== 主体管理（Element） ====================

    /**
     * 创建自定义主体（异步，返回 task_id）
     */
    public JsonNode createElement(Map<String, Object> body) {
        return post(KlingConstants.PATH_ELEMENT_CREATE, body, JsonNode.class);
    }

    /**
     * 查询单个主体创建任务状态
     */
    public JsonNode getElementTask(String taskId) {
        return get(KlingConstants.PATH_ELEMENT_QUERY + "/" + taskId, JsonNode.class);
    }

    /**
     * 查询自定义主体列表（分页）
     */
    public JsonNode listCustomElements(int pageNum, int pageSize) {
        String path = KlingConstants.PATH_ELEMENT_QUERY + "?pageNum=" + pageNum + "&pageSize=" + pageSize;
        return get(path, JsonNode.class);
    }

    /**
     * 查询官方预设主体列表（分页）
     */
    public JsonNode listPresetElements(int pageNum, int pageSize) {
        String path = KlingConstants.PATH_ELEMENT_PRESETS + "?pageNum=" + pageNum + "&pageSize=" + pageSize;
        return get(path, JsonNode.class);
    }

    /**
     * 删除主体
     */
    public JsonNode deleteElement(String elementId) {
        Map<String, Object> body = Map.of("element_id", elementId);
        return post(KlingConstants.PATH_ELEMENT_DELETE, body, JsonNode.class);
    }
}