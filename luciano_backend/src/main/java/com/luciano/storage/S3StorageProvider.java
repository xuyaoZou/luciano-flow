package com.luciano.storage;

import com.luciano.entity.StorageProviderConfig;
import com.luciano.service.StorageProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 通用 S3 兼容存储实现（纯 Java，S3 V4 签名 + HttpURLConnection）
 * <p>
 * 支持：火山引擎 TOS、MinIO、AWS S3、阿里云 OSS（S3 兼容模式）等所有 S3 协议存储。
 * <p>
 * 配置项（从 storage_providers.config JSON 读取）：
 * - endpoint: S3 端点（如 tos-s3-cn-beijing.volces.com、minio.example.com:9000）
 * - region: 区域（默认 us-east-1，MinIO 用 default）
 * - bucket: 桶名
 * - accessKeyId: AK
 * - secretAccessKey: SK
 * - publicUrl: 公网访问前缀（如 https://bucket.tos-cn-beijing.volces.com）
 * - pathStyle: 是否使用路径风格访问（MinIO 默认 true，TOS/AWS 用 false/虚拟主机风格）
 */
@Slf4j
@Component("storageProvider_s3")
public class S3StorageProvider implements StorageProvider {

    private final StorageProviderService providerService;
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";

    public S3StorageProvider(StorageProviderService providerService) {
        this.providerService = providerService;
    }

    @Override
    public String upload(String key, InputStream data, String contentType) {
        StorageProviderConfig config = getS3Config();
        String endpoint = config.getConfigString("endpoint");
        String region = config.getConfigString("region");
        String bucket = config.getConfigString("bucket");
        String ak = config.getConfigString("accessKeyId");
        String sk = config.getConfigString("secretAccessKey");
        String publicUrl = config.getConfigString("publicUrl");
        boolean pathStyle = isPathStyle(config);

        try {
            byte[] bytes = data.readAllBytes();
            String contentLength = String.valueOf(bytes.length);

            SimpleDateFormat amzFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd");
            amzFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date now = new Date();
            String amzDate = amzFormat.format(now);
            String dateStamp = dayFormat.format(now);

            String payloadHash = sha256Hex(bytes);

            // 构建请求 URL 和 Host
            String host;
            String urlStr;
            String canonicalUri;
            if (pathStyle) {
                // 路径风格：Host 是 endpoint，URI 是 /bucket/key
                host = endpoint.contains("://") ? endpoint.substring(endpoint.indexOf("://") + 3) : endpoint;
                urlStr = "https://" + host + "/" + bucket + "/" + key;
                canonicalUri = "/" + bucket + "/" + key;
            } else {
                // 虚拟主机风格：Host 是 bucket.endpoint，URI 是 /key
                host = bucket + "." + endpoint;
                urlStr = "https://" + host + "/" + key;
                canonicalUri = "/" + key;
            }

            String canonicalHeaders = "content-type:" + contentType + "\n" +
                    "host:" + host + "\n" +
                    "x-amz-content-sha256:" + payloadHash + "\n" +
                    "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";
            String canonicalRequest = "PUT\n" + canonicalUri + "\n\n" +
                    canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

            String credentialScope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
            String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" +
                    sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

            byte[] signingKey = getSignatureKey(sk, dateStamp, region, SERVICE);
            String signature = hexEncode(hmacSHA256(signingKey, stringToSign));
            String authorization = ALGORITHM + " Credential=" + ak + "/" + credentialScope +
                    ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Content-Length", contentLength);
            conn.setRequestProperty("Host", host);
            conn.setRequestProperty("x-amz-date", amzDate);
            conn.setRequestProperty("x-amz-content-sha256", payloadHash);
            conn.setRequestProperty("Authorization", authorization);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            conn.getOutputStream().write(bytes);

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                String resultUrl = publicUrl + "/" + key;
                log.info("[S3Storage] Uploaded: key={}, url={}, size={}, host={}", key, resultUrl, bytes.length, host);
                return resultUrl;
            } else {
                String errorBody;
                try (InputStream errStream = conn.getErrorStream()) {
                    errorBody = errStream != null ? new String(errStream.readAllBytes(), StandardCharsets.UTF_8) : "no error body";
                }
                throw new RuntimeException("S3 upload failed: HTTP " + responseCode + " - " + errorBody);
            }

        } catch (Exception e) {
            throw new RuntimeException("S3 upload failed: key=" + key + ", error=" + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        StorageProviderConfig config = getS3Config();
        String endpoint = config.getConfigString("endpoint");
        String region = config.getConfigString("region");
        String bucket = config.getConfigString("bucket");
        String ak = config.getConfigString("accessKeyId");
        String sk = config.getConfigString("secretAccessKey");
        boolean pathStyle = isPathStyle(config);

        try {
            String host;
            String urlStr;
            String canonicalUri;
            if (pathStyle) {
                host = endpoint.contains("://") ? endpoint.substring(endpoint.indexOf("://") + 3) : endpoint;
                urlStr = "https://" + host + "/" + bucket + "/" + key;
                canonicalUri = "/" + bucket + "/" + key;
            } else {
                host = bucket + "." + endpoint;
                urlStr = "https://" + host + "/" + key;
                canonicalUri = "/" + key;
            }

            SimpleDateFormat amzFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd");
            amzFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date now = new Date();
            String amzDate = amzFormat.format(now);
            String dateStamp = dayFormat.format(now);

            String payloadHash = sha256Hex(new byte[0]);
            String canonicalHeaders = "host:" + host + "\n" +
                    "x-amz-content-sha256:" + payloadHash + "\n" +
                    "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
            String canonicalRequest = "DELETE\n" + canonicalUri + "\n\n" +
                    canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

            String credentialScope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
            String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" +
                    sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = getSignatureKey(sk, dateStamp, region, SERVICE);
            String signature = hexEncode(hmacSHA256(signingKey, stringToSign));
            String authorization = ALGORITHM + " Credential=" + ak + "/" + credentialScope +
                    ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Host", host);
            conn.setRequestProperty("x-amz-date", amzDate);
            conn.setRequestProperty("x-amz-content-sha256", payloadHash);
            conn.setRequestProperty("Authorization", authorization);

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                log.info("[S3Storage] Deleted: key={}", key);
            } else {
                log.warn("[S3Storage] Delete failed: HTTP {} for key={}", code, key);
            }
        } catch (Exception e) {
            log.error("[S3Storage] Delete error: key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public String getPublicUrl(String key) {
        StorageProviderConfig config = getS3Config();
        return config.getConfigString("publicUrl") + "/" + key;
    }

    @Override
    public String getType() {
        return "s3";
    }

    /**
     * 从 S3 下载文件，返回 InputStream
     * 用于代理返回给前端（解决 fetch 302 重定向丢失 Authorization 的问题）
     */
    @Override
    public InputStream download(String key) {
        StorageProviderConfig config = getS3Config();
        String endpoint = config.getConfigString("endpoint");
        String region = config.getConfigString("region");
        String bucket = config.getConfigString("bucket");
        String ak = config.getConfigString("accessKeyId");
        String sk = config.getConfigString("secretAccessKey");
        String publicUrl = config.getConfigString("publicUrl");
        boolean pathStyle = isPathStyle(config);

        try {
            String host;
            String urlStr;
            String canonicalUri;
            if (pathStyle) {
                host = endpoint.contains("://") ? endpoint.substring(endpoint.indexOf("://") + 3) : endpoint;
                urlStr = "https://" + host + "/" + bucket + "/" + key;
                canonicalUri = "/" + bucket + "/" + key;
            } else {
                host = bucket + "." + endpoint;
                urlStr = "https://" + host + "/" + key;
                canonicalUri = "/" + key;
            }

            SimpleDateFormat amzFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd");
            amzFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date now = new Date();
            String amzDate = amzFormat.format(now);
            String dateStamp = dayFormat.format(now);

            String canonicalHeaders = "host:" + host.toLowerCase() + "\nx-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\nx-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
            String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";

            String canonicalRequest = "GET\n" + canonicalUri + "\n\n" +
                    canonicalHeaders + "\n" + signedHeaders + "\n" +
                            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

            String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" +
                    sha256Hex(canonicalRequest);

            byte[] signingKey = getSignatureKey(sk, dateStamp, region, SERVICE);
            String signature = hexEncode(hmacSHA256(signingKey, stringToSign));

            String authorization = ALGORITHM + " Credential=" + ak + "/" + credentialScope +
                    ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Host", host.toLowerCase());
            conn.setRequestProperty("x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
            conn.setRequestProperty("x-amz-date", amzDate);
            conn.setRequestProperty("Authorization", authorization);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return conn.getInputStream();
            } else {
                String errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("S3 download failed: HTTP " + responseCode + " - " + errorBody);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("S3 download failed: key=" + key + ", error=" + e.getMessage(), e);
        }
    }

    /**
     * 获取默认 provider 配置（必须是 s3 类型）
     * 兼容旧的 "tos" 类型配置 — TOS 本质也是 S3
     */
    private StorageProviderConfig getS3Config() {
        StorageProviderConfig config = providerService.getDefault();
        String type = config.getProviderType();
        if (!"s3".equals(type) && !"tos".equals(type) && !"minio".equals(type)) {
            throw new IllegalStateException("Default provider is not S3-compatible: " + type);
        }
        return config;
    }

    /**
     * 判断是否使用路径风格访问
     * MinIO 默认路径风格，TOS/AWS 默认虚拟主机风格
     */
    private boolean isPathStyle(StorageProviderConfig config) {
        Object pathStyleObj = config.getConfig() != null ? config.getConfig().get("pathStyle") : null;
        if (pathStyleObj != null) {
            if (pathStyleObj instanceof Boolean) return (Boolean) pathStyleObj;
            if (pathStyleObj instanceof String) return Boolean.parseBoolean((String) pathStyleObj);
        }
        // 默认：MinIO 用路径风格，其他用虚拟主机风格
        return "minio".equals(config.getProviderType());
    }

    // ========== S3 V4 签名工具方法 ==========

    private byte[] hmacSHA256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] getSignatureKey(String secretKey, String dateStamp, String region, String service) throws Exception {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSHA256(kSecret, dateStamp);
        byte[] kRegion = hmacSHA256(kDate, region);
        byte[] kService = hmacSHA256(kRegion, service);
        return hmacSHA256(kService, "aws4_request");
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return hexEncode(md.digest(data));
    }

    private String sha256Hex(String data) throws Exception {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    private String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}