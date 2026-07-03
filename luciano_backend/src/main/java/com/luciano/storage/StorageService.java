package com.luciano.storage;

import com.luciano.entity.StorageProviderConfig;
import com.luciano.service.StorageProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一存储服务 — 根据数据库配置动态路由到对应 StorageProvider
 * <p>
 * 支持的类型：local / s3（兼容 TOS/MinIO/AWS 等）/ oss
 * <p>
 * 新增 provider 类型只需：1. 实现 StorageProvider 接口 2. 注册为 Spring Bean（Bean 名 = storageProvider_xxx）
 */
@Slf4j
@Service
public class StorageService {

    private final StorageProviderService providerService;
    private final Map<String, StorageProvider> providerMap;

    /**
     * Spring 自动注入所有 StorageProvider 实现，按 Bean 名映射
     * Bean 名格式：storageProvider_{type}（如 storageProvider_s3、storageProvider_local）
     */
    public StorageService(StorageProviderService providerService, List<StorageProvider> providers) {
        this.providerService = providerService;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(StorageProvider::getType, p -> p));
        log.info("[StorageService] Registered providers: {}", providerMap.keySet());
    }

    /**
     * 上传文件到默认存储
     */
    public String upload(String key, InputStream data, String contentType) {
        return upload(null, key, data, contentType);
    }

    /**
     * 上传文件到指定存储
     */
    public String upload(Long providerId, String key, InputStream data, String contentType) {
        StorageProviderConfig config = getProvider(providerId);
        StorageProvider provider = resolveProvider(config.getProviderType());
        return provider.upload(key, data, contentType);
    }

    /**
     * 获取公网 URL
     */
    public String getPublicUrl(Long providerId, String key) {
        StorageProviderConfig config = getProvider(providerId);
        StorageProvider provider = resolveProvider(config.getProviderType());

        // 优先用 provider 自己的 getPublicUrl（可能需要拼接逻辑）
        String url = provider.getPublicUrl(key);
        if (url != null) return url;

        // 回退：用数据库配置的 publicUrl 拼接
        String customUrl = config.getConfigString("publicUrl");
        if (customUrl != null) return customUrl + "/" + key;

        // local 回退：用配置的 publicUrl + API 路径
        if ("local".equals(config.getProviderType())) {
            return config.getPublicUrl() + "/api/v1/media/key/" + key;
        }

        throw new IllegalStateException("Cannot determine public URL for provider type: " + config.getProviderType());
    }

    /**
     * 删除文件
     */
    public void delete(Long providerId, String key) {
        StorageProviderConfig config = getProvider(providerId);
        StorageProvider provider = resolveProvider(config.getProviderType());
        provider.delete(key);
    }

    /**
     * 删除文件（用默认存储）
     */
    public void delete(String key) {
        delete(null, key);
    }

    private StorageProviderConfig getProvider(Long providerId) {
        return providerService.getById(providerId);
    }

    /**
     * 根据 provider 类型解析到对应的 StorageProvider 实现
     * 兼容映射：tos → s3, minio → s3
     */
    private StorageProvider resolveProvider(String providerType) {
        if (providerMap.containsKey(providerType)) {
            return providerMap.get(providerType);
        }
        // 兼容映射：tos/minio 统一走 s3
        if ("tos".equals(providerType) || "minio".equals(providerType)) {
            StorageProvider s3Provider = providerMap.get("s3");
            if (s3Provider != null) return s3Provider;
        }
        throw new IllegalArgumentException("Unknown storage provider type: " + providerType +
                ", available: " + providerMap.keySet());
    }

    public StorageProviderConfig getDefaultProvider() {
        return providerService.getDefault();
    }

    /**
     * 获取所有启用的 provider 配置
     */
    public List<StorageProviderConfig> listEnabled() {
        return providerService.listEnabled();
    }

    /**
     * 从对象存储下载文件，返回为 Spring Resource（用于代理返回给前端）
     * <p>
     * 解决 fetch 跟随 302 重定向丢失 Authorization header 的问题：
     * 后端代理下载对象存储文件内容，直接返回给前端，不做 302 重定向。
     *
     * @param providerId 存储提供者 ID
     * @param key 存储键（如 image/upload/xxx.jpg）
     * @param path 用于日志的路径标识
     * @return Resource 可直接返回给前端
     */
    public Resource downloadAsResource(Long providerId, String key, String path) {
        StorageProviderConfig config = getProvider(providerId);
        StorageProvider provider = resolveProvider(config.getProviderType());

        // 本地存储：直接返回文件
        if ("local".equals(config.getProviderType())) {
            String localPath = config.getConfigString("path");
            if (localPath != null) {
                java.nio.file.Path filePath = java.nio.file.Path.of(localPath, path != null ? path : key);
                if (java.nio.file.Files.exists(filePath)) {
                    return new org.springframework.core.io.FileSystemResource(filePath);
                }
            }
            throw new IllegalStateException("Local file not found: " + key);
        }

        // 对象存储：下载文件内容，包装为 ByteArrayResource
        try {
            InputStream stream = provider.download(key);
            if (stream == null) {
                throw new IllegalStateException("Download returned null for key: " + key);
            }
            byte[] bytes = stream.readAllBytes();
            stream.close();
            log.debug("[StorageService] Downloaded {} bytes from key={}", bytes.length, key);
            return new ByteArrayResource(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download from object storage: " + e.getMessage(), e);
        }
    }
}