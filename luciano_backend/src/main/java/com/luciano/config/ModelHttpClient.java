package com.luciano.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.entity.ApiOperationLog;
import com.luciano.service.ApiOperationLogService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 通用模型 HTTP 客户端
 * 统一处理：重试、超时、错误日志、请求构建、操作日志
 */
@Component
@Slf4j
public class ModelHttpClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiOperationLogService operationLogService;

    public ModelHttpClient(ObjectMapper objectMapper, ApiOperationLogService operationLogService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.operationLogService = operationLogService;
    }

    // ==================== 日志上下文（ThreadLocal） ====================

    /**
     * 操作日志上下文 — 调用方通过 ThreadLocal 传入用户/关联信息
     */
    public static class LogContext {
        private static final ThreadLocal<LogContext> CURRENT = new ThreadLocal<>();

        private Long userId;
        private String username;
        private String adapterId;
        private String capability;
        private String operationType;  // submit / poll / download / complete
        private String taskId;
        private String providerTaskId;
        private Long projectId;
        private Long episodeId;
        private Long storyboardId;
        private String platformStatus;  // 平台原始状态

        public static LogContext current() {
            return CURRENT.get();
        }

        public static void set(LogContext ctx) {
            CURRENT.set(ctx);
        }

        public static void clear() {
            CURRENT.remove();
        }

        // Builder-style setters
        public LogContext userId(Long v) { this.userId = v; return this; }
        public LogContext username(String v) { this.username = v; return this; }
        public LogContext adapterId(String v) { this.adapterId = v; return this; }
        public LogContext capability(String v) { this.capability = v; return this; }
        public LogContext operationType(String v) { this.operationType = v; return this; }
        public LogContext taskId(String v) { this.taskId = v; return this; }
        public LogContext providerTaskId(String v) { this.providerTaskId = v; return this; }
        public LogContext projectId(Long v) { this.projectId = v; return this; }
        public LogContext episodeId(Long v) { this.episodeId = v; return this; }
        public LogContext storyboardId(Long v) { this.storyboardId = v; return this; }
        public LogContext platformStatus(String v) { this.platformStatus = v; return this; }
    }

    // ==================== HTTP 方法 ====================

    /**
     * GET 请求
     */
    public <T> T get(String url, Headers headers, Class<T> responseType) {
        return execute(HttpMethod.GET, url, headers, null, responseType, 0);
    }

    /**
     * POST 请求
     */
    public <T> T post(String url, Headers headers, Object body, Class<T> responseType) {
        return execute(HttpMethod.POST, url, headers, body, responseType, 0);
    }

    /**
     * POST 请求（带重试）
     */
    public <T> T post(String url, Headers headers, Object body, Class<T> responseType, int maxRetries) {
        return execute(HttpMethod.POST, url, headers, body, responseType, maxRetries);
    }

    /**
     * 通用请求执行（带重试 + 操作日志）
     */
    private <T> T execute(HttpMethod method, String url, Headers headers, Object body,
                          Class<T> responseType, int maxRetries) {
        int attempts = maxRetries + 1;
        Exception lastException = null;

        long startTime = System.currentTimeMillis();
        String requestBodyStr = null;
        String responseBodyStr = null;
        int responseStatusCode = 0;
        String errorCode = null;
        String errorMessage = null;

        LogContext currentCtx = LogContext.current();
        log.debug("[ModelHttpClient] execute {} {}, LogContext present={}", method, url, currentCtx != null);

        // 序列化请求体（脱敏）
        try {
            if (body != null) {
                requestBodyStr = sanitizeRequestBody(objectMapper.writeValueAsString(body));
            }
        } catch (Exception e) {
            requestBodyStr = "{}";
        }

        for (int i = 0; i < attempts; i++) {
            try {
                HttpHeaders httpHeaders = new HttpHeaders();
                if (headers != null) {
                    headers.getHeaders().forEach(httpHeaders::set);
                }
                httpHeaders.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<String> entity;
                if (body != null) {
                    String jsonBody = objectMapper.writeValueAsString(body);
                    entity = new HttpEntity<>(jsonBody, httpHeaders);
                } else {
                    entity = new HttpEntity<>(httpHeaders);
                }

                ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
                responseStatusCode = response.getStatusCode().value();
                responseBodyStr = truncate(response.getBody(), 4096);

                // 提取 Kling 扣费信息
                BigDecimal creditsUsed = extractCredits(responseBodyStr);
                String resultUrl = extractResultUrl(responseBodyStr);

                // 记录成功日志
                long duration = System.currentTimeMillis() - startTime;
                saveOperationLog(method, url, requestBodyStr, responseStatusCode, responseBodyStr,
                        null, null, duration, creditsUsed, resultUrl);

                if (responseType == String.class) {
                    @SuppressWarnings("unchecked")
                    T result = (T) response.getBody();
                    return result;
                }
                return objectMapper.readValue(response.getBody(), responseType);

            } catch (HttpClientErrorException e) {
                // 4xx 不重试
                responseStatusCode = e.getStatusCode().value();
                responseBodyStr = truncate(e.getResponseBodyAsString(), 4096);
                errorCode = extractErrorCode(responseBodyStr);
                errorMessage = truncate(e.getResponseBodyAsString(), 1000);
                log.error("HTTP {} error for {}: status={}, body={}", method, url, e.getStatusCode(), e.getResponseBodyAsString());

                long duration = System.currentTimeMillis() - startTime;
                saveOperationLog(method, url, requestBodyStr, responseStatusCode, responseBodyStr,
                        errorCode, errorMessage, duration, null, null);

                throw new ModelHttpException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);

            } catch (HttpServerErrorException e) {
                // 5xx 可重试
                lastException = e;
                responseStatusCode = e.getStatusCode().value();
                errorCode = String.valueOf(e.getStatusCode().value());
                errorMessage = truncate(e.getResponseBodyAsString(), 1000);
                log.warn("HTTP {} error for {} (attempt {}/{}): status={}",
                        method, url, i + 1, attempts, e.getStatusCode());
                if (i < attempts - 1) {
                    sleep(2000);
                }

            } catch (ResourceAccessException e) {
                // 连接超时/网络错误可重试
                lastException = e;
                errorCode = "NETWORK_ERROR";
                errorMessage = e.getMessage();
                log.warn("Network error for {} (attempt {}/{}): {}", url, i + 1, attempts, e.getMessage());
                if (i < attempts - 1) {
                    sleep(3000);
                }

            } catch (Exception e) {
                log.error("Unexpected error for {}: {}", url, e.getMessage(), e);
                long duration = System.currentTimeMillis() - startTime;
                saveOperationLog(method, url, requestBodyStr, 500, null,
                        "INTERNAL_ERROR", e.getMessage(), duration, null, null);
                throw new ModelHttpException(500, e.getMessage(), e);
            }
        }

        // 所有重试都失败
        long duration = System.currentTimeMillis() - startTime;
        saveOperationLog(method, url, requestBodyStr, responseStatusCode, responseBodyStr,
                errorCode, errorMessage, duration, null, null);

        throw new ModelHttpException(500, "Max retries exceeded for " + url, lastException);
    }

    // ==================== 操作日志 ====================

    /**
     * 异步写入操作日志
     */
    private void saveOperationLog(HttpMethod method, String url, String requestBody,
                                   Integer responseStatus, String responseBody,
                                   String errorCode, String errorMessage,
                                   long durationMs, BigDecimal creditsUsed, String resultUrl) {
        try {
            LogContext ctx = LogContext.current();
            if (ctx == null) {
                // 没有 LogContext，跳过日志（避免无意义的记录）
                return;
            }

            ApiOperationLog logEntry = new ApiOperationLog();
            logEntry.setUserId(ctx.userId);
            logEntry.setUsername(ctx.username);
            logEntry.setAdapterId(ctx.adapterId);
            logEntry.setCapability(ctx.capability);
            logEntry.setOperationType(ctx.operationType);
            logEntry.setTaskId(ctx.taskId);
            logEntry.setProviderTaskId(ctx.providerTaskId);
            logEntry.setProjectId(ctx.projectId);
            logEntry.setEpisodeId(ctx.episodeId);
            logEntry.setStoryboardId(ctx.storyboardId);
            logEntry.setPlatformStatus(ctx.platformStatus);

            logEntry.setMethod(method.name());
            logEntry.setPath(truncate(url, 512));
            logEntry.setRequestBody(requestBody);
            logEntry.setResponseStatus(responseStatus);
            logEntry.setResponseBody(responseBody);
            logEntry.setErrorCode(errorCode);
            logEntry.setErrorMessage(errorMessage);
            logEntry.setDurationMs((int) durationMs);
            logEntry.setCreditsUsed(creditsUsed);
            logEntry.setResultUrl(resultUrl != null ? truncate(resultUrl, 2000) : null);
            logEntry.setCreatedAt(OffsetDateTime.now());

            operationLogService.saveAsync(logEntry);
        } catch (Exception e) {
            // 日志写入失败不能影响主流程
            log.warn("[ModelHttpClient] Failed to save operation log: {}", e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 脱敏请求体：移除 API Key / Secret / Token 等敏感字段
     */
    private String sanitizeRequestBody(String json) {
        if (json == null) return null;
        // 脱敏常见敏感字段
        json = json.replaceAll("(\"(?:api_key|api_secret|access_key|secret_key|token|authorization|password)\"\\s*:\\s*\")([^\"]{4})([^\"]*)(\")", "$1****$4");
        json = json.replaceAll("(\"(?:Authorization)\"\\s*:\\s*\")([^\"]{8})([^\"]*)(\")", "$1****$4");
        return truncate(json, 16384);  // 请求体限制 16KB
    }

    /**
     * 从 Kling 响应中提取扣费
     */
    private BigDecimal extractCredits(String responseBody) {
        try {
            if (responseBody == null) return null;
            var node = objectMapper.readTree(responseBody);
            // Kling: data.final_unit_deduction
            var deduction = node.at("/data/final_unit_deduction");
            if (!deduction.isMissingNode() && deduction.isValueNode()) {
                return new BigDecimal(deduction.asText());
            }
            // Kling: data.final_balance_deduction.quota
            var quota = node.at("/data/final_balance_deduction/quota");
            if (!quota.isMissingNode() && quota.isValueNode()) {
                return new BigDecimal(quota.asText());
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 从 Kling 响应中提取结果 URL
     */
    private String extractResultUrl(String responseBody) {
        try {
            if (responseBody == null) return null;
            var node = objectMapper.readTree(responseBody);
            // Kling: data.task_result.videos[0].url
            var url = node.at("/data/task_result/videos/0/url");
            if (!url.isMissingNode()) return url.asText();
            // Kling: data.task_result/images[0].url
            url = node.at("/data/task_result/images/0/url");
            if (!url.isMissingNode()) return url.asText();
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 从 Kling 响应中提取业务错误码
     */
    private String extractErrorCode(String responseBody) {
        try {
            if (responseBody == null) return null;
            var node = objectMapper.readTree(responseBody);
            var code = node.get("code");
            if (code != null && code.isValueNode()) {
                return String.valueOf(code.asInt());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 内部类 ====================

    /**
     * 请求头构建器
     */
    @Data
    @Builder
    public static class Headers {
        @Builder.Default
        private Map<String, String> headers = new java.util.HashMap<>();

        public static Headers of(String key, String value) {
            return Headers.builder().headers(new java.util.HashMap<>(Map.of(key, value))).build();
        }

        public static Headers of(Map<String, String> headers) {
            return Headers.builder().headers(new java.util.HashMap<>(headers)).build();
        }

        public Headers add(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }
    }

    /**
     * 模型 HTTP 异常
     */
    public static class ModelHttpException extends RuntimeException {
        private final int statusCode;

        public ModelHttpException(int statusCode, String message, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}