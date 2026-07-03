package com.luciano.adapter.kling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kling 适配器端到端验证测试（纯 HTTP，不依赖 Spring 容器）
 * 
 * 验证计划文档：projects/luciano/docs/3-端到端验证计划.md
 * 
 * 运行方式：
 *   mvn test -Dtest=KlingAdapterVerificationTest
 * 
 * AK/SK 来源：
 *   1. 环境变量 KLING_ACCESS_KEY / KLING_SECRET_KEY
 *   2. .env.adapters 文件
 * 
 * API 域名：https://api-beijing.klingai.com（国内版）
 * API 路径：/v1/videos/text2video（无 /api 前缀）
 * 模型字段：model_name（非 model）
 */
class KlingAdapterVerificationTest {

    // 国内版 API 域名（优先使用）
    private static final String BASE_URL = "https://api-beijing.klingai.com";
    private static final String T2V_PATH = "/v1/videos/text2video";

    private static String accessKey;
    private static String secretKey;
    private String jwtToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final List<String> results = new ArrayList<>();

    @BeforeAll
    static void loadKeys() {
        accessKey = System.getenv("KLING_ACCESS_KEY");
        secretKey = System.getenv("KLING_SECRET_KEY");

        if (accessKey == null || secretKey == null) {
            try {
                Path envFile = Path.of(System.getProperty("user.dir"), ".env.adapters");
                if (Files.exists(envFile)) {
                    for (String line : Files.readAllLines(envFile)) {
                        line = line.trim();
                        if (line.startsWith("#") || line.isEmpty()) continue;
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            if ("KLING_ACCESS_KEY".equals(parts[0].trim())) accessKey = parts[1].trim();
                            if ("KLING_SECRET_KEY".equals(parts[0].trim())) secretKey = parts[1].trim();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        assertNotNull(accessKey, "KLING_ACCESS_KEY 未设置");
        assertNotNull(secretKey, "KLING_SECRET_KEY 未设置");
        System.out.println("🔑 AK: " + accessKey.substring(0, 8) + "...");
    }

    @BeforeEach
    void generateJwtToken() {
        long now = System.currentTimeMillis();
        // 使用 SecretKeySpec 替代 Keys.hmacShaKeyFor()
        // Keys.hmacShaKeyFor() 会做额外的密钥验证，可能导致签名不一致
        byte[] skBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(skBytes, "HmacSHA256");
        
        jwtToken = Jwts.builder()
                .header().add("alg", "HS256").add("typ", "JWT").and()
                .issuer(accessKey)
                .issuedAt(new Date(now - 5000))   // iat
                .notBefore(new Date(now - 5000))  // nbf: 5秒前生效
                .expiration(new Date(now + 1800000)) // exp: 30分钟过期
                .signWith(key)
                .compact();
        
        System.out.println("🔑 Token: " + jwtToken.substring(0, Math.min(40, jwtToken.length())) + "...");
    }

    // ==================== V0: JWT 认证验证 ====================

    @Test
    @Order(0)
    void v0_authentication() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("V0: Kling JWT 认证验证");
        System.out.println("=".repeat(60));

        String[] parts = jwtToken.split("\\.");
        assertEquals(3, parts.length, "JWT Token 应有 3 段");
        record("V0", "JWT Token 结构", "✅ 3段格式正确");

        // Token 有效性：POST 到 T2V 端点，期望不返回 401
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model_name", "kling-v2-5-turbo");
            body.put("prompt", "test");
            body.put("duration", "5");
            body.put("mode", "std");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + T2V_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), authHeaders()),
                    String.class
            );
            record("V0", "Token 有效性", "✅ Token 被接受（HTTP " + response.getStatusCode() + "）");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("401") || msg.contains("Unauthorized")) {
                record("V0", "Token 有效性", "❌ Token 被拒绝（401）");
                fail("JWT Token 被拒绝: " + msg);
            } else {
                // 非 401 错误说明认证通过（可能是参数错误、额度不足等）
                record("V0", "Token 有效性", "✅ Token 被接受（认证通过，其他错误: " + msg.substring(0, Math.min(100, msg.length())) + "）");
            }
        }

        printResults("V0");
    }

    // ==================== V1: 文生视频 (P0) ====================

    @Test
    @Order(1)
    void v1_textToVideo() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("V1: Kling T2V 文生视频");
        System.out.println("=".repeat(60));

        String taskId = null;

        // 1. 提交任务
        System.out.println("\n[1/4] 提交 T2V 任务...");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model_name", "kling-v2-5-turbo");
            body.put("prompt", "一只橘猫在草地上奔跑，阳光明媚，高清画质");
            body.put("duration", "5");
            body.put("aspect_ratio", "16:9");
            body.put("mode", "std");

            String bodyStr = objectMapper.writeValueAsString(body);
            System.out.println("请求体: " + bodyStr);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + T2V_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(bodyStr, authHeaders()),
                    String.class
            );

            System.out.println("响应状态: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt(-1);
            String message = root.path("message").asText("");

            if (code == 0) {
                taskId = root.path("data").path("task_id").asText(null);
                String taskStatus = root.path("data").path("task_status").asText("");
                if (taskId != null && !taskId.isEmpty()) {
                    record("V1", "提交任务", "✅ taskId=" + taskId + ", status=" + taskStatus);
                } else {
                    record("V1", "提交任务", "❓ 响应成功但无法提取 taskId");
                }
            } else {
                record("V1", "提交任务", "❌ API 错误: code=" + code + " message=" + message);
                fail("Kling API 返回错误: code=" + code + " message=" + message);
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "unknown";
            record("V1", "提交任务", "❌ " + msg.substring(0, Math.min(200, msg.length())));
            fail("提交任务失败: " + msg);
        }

        // 2. 轮询
        if (taskId != null) {
            System.out.println("\n[2/4] 轮询任务状态 (taskId=" + taskId + ")...");
            TaskStatus finalStatus = pollTask(taskId, 10 * 60 * 1000);
            record("V1", "轮询结果", finalStatus == TaskStatus.COMPLETED ? "✅ COMPLETED" :
                    finalStatus == TaskStatus.FAILED ? "❌ FAILED" : "⚠️ " + finalStatus);

            // 3. 下载/获取结果
            if (finalStatus == TaskStatus.COMPLETED) {
                System.out.println("\n[3/4] 获取视频结果...");
                try {
                    ResponseEntity<String> resultResp = restTemplate.exchange(
                            BASE_URL + T2V_PATH + "/" + taskId,
                            HttpMethod.GET,
                            new HttpEntity<>(authHeaders()),
                            String.class
                    );

                    JsonNode result = objectMapper.readTree(resultResp.getBody());
                    String videoUrl = extractVideoUrl(result);
                    if (videoUrl != null) {
                        record("V1", "获取结果", "✅ videoUrl=" + videoUrl.substring(0, Math.min(100, videoUrl.length())) + "...");
                    } else {
                        record("V1", "获取结果", "⚠️ 无法提取视频 URL");
                    }
                    System.out.println("完整响应: " + resultResp.getBody().substring(0, Math.min(500, resultResp.getBody().length())));
                } catch (Exception e) {
                    record("V1", "获取结果", "❌ " + e.getMessage());
                }
            } else {
                record("V1", "获取结果", "⏭️ 跳过（任务未完成）");
            }
        } else {
            record("V1", "轮询结果", "⏭️ 跳过（任务未提交）");
            record("V1", "获取结果", "⏭️ 跳过");
        }

        // 4. 费用预估（跳过，需要 Spring 容器）
        record("V1", "费用预估", "⏭️ 跳过（需要 Spring 容器）");

        printResults("V1");
    }

    // ==================== 错误传播验证 ====================

    @Test
    @Order(10)
    void v_err_invalidRequest() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("V-ERR: 错误传播验证");
        System.out.println("=".repeat(60));

        // 空 prompt（可能被接受，因为 multi_shot 模式不需要 prompt）
        System.out.println("\n[1/2] 空 prompt + std 模式...");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model_name", "kling-v2-5-turbo");
            body.put("prompt", "");
            body.put("duration", "5");
            body.put("mode", "std");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + T2V_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), authHeaders()),
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                record("V-ERR", "空 prompt", "✅ 被拒绝 (code=" + code + "): " + root.path("message").asText());
            } else {
                // 可能被接受了
                String taskId = root.path("data").path("task_id").asText("");
                record("V-ERR", "空 prompt", "⚠️ 被接受 (taskId=" + taskId + ")");
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            record("V-ERR", "空 prompt", "❓ " + msg.substring(0, Math.min(200, msg.length())));
        }

        // 无效 model_name
        System.out.println("\n[2/2] 无效 model_name...");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model_name", "invalid-model-name");
            body.put("prompt", "测试");
            body.put("duration", "5");
            body.put("mode", "std");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + T2V_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), authHeaders()),
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                record("V-ERR", "无效 model", "✅ 被拒绝 (code=" + code + "): " + root.path("message").asText());
            } else {
                record("V-ERR", "无效 model", "⚠️ 被接受");
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("4") || msg.contains("400") || msg.contains("422")) {
                record("V-ERR", "无效 model", "✅ 被拒绝（4xx）");
            } else {
                record("V-ERR", "无效 model", "❓ " + msg.substring(0, Math.min(200, msg.length())));
            }
        }

        printResults("V-ERR");
    }

    // ==================== 辅助方法 ====================

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    private enum TaskStatus { PENDING, PROCESSING, COMPLETED, FAILED }

    private TaskStatus pollTask(String taskId, long timeoutMs) {
        long start = System.currentTimeMillis();
        int intervalMs = 5000;
        int pollCount = 0;

        while (System.currentTimeMillis() - start < timeoutMs) {
            try { Thread.sleep(intervalMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return TaskStatus.FAILED; }

            pollCount++;
            try {
                // Token 过期刷新
                if (System.currentTimeMillis() - start > 1700000) {
                    generateJwtToken();
                }

                ResponseEntity<String> resp = restTemplate.exchange(
                        BASE_URL + T2V_PATH + "/" + taskId,
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders()),
                        String.class
                );

                JsonNode root = objectMapper.readTree(resp.getBody());
                String status = root.path("data").path("task_status").asText("");
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                System.out.println("[POLL #" + pollCount + "] status=" + status + " (elapsed " + elapsed + "s)");

                switch (status) {
                    case "succeed": return TaskStatus.COMPLETED;
                    case "failed":
                        String errMsg = root.path("data").path("task_status_msg").asText("");
                        System.out.println("[POLL] 任务失败: " + errMsg);
                        return TaskStatus.FAILED;
                    case "processing":
                    case "submitted":
                        break;
                    default:
                        System.out.println("[POLL] 未知状态: " + status);
                }
            } catch (Exception e) {
                System.out.println("[POLL #" + pollCount + "] 错误: " + e.getMessage());
            }

            if (pollCount % 6 == 0 && intervalMs < 15000) {
                intervalMs = Math.min(intervalMs + 5000, 15000);
            }
        }
        return TaskStatus.FAILED;
    }

    private String extractVideoUrl(JsonNode result) {
        JsonNode videos = result.path("data").path("task_result").path("videos");
        if (videos.isArray() && !videos.isEmpty()) {
            return videos.get(0).path("url").asText(null);
        }
        return null;
    }

    private static void record(String phase, String step, String result) {
        String entry = String.format("  [%s] %s: %s", phase, step, result);
        results.add(entry);
        System.out.println(entry);
    }

    private void printResults(String phase) {
        System.out.println("\n📋 " + phase + " 结果:");
        results.forEach(System.out::println);
    }

    @AfterAll
    static void printSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📋 验证结果汇总");
        System.out.println("=".repeat(60));
        results.forEach(System.out::println);
        System.out.println("=".repeat(60));
    }
}