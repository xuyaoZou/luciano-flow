package com.luciano.service;

import com.luciano.entity.MediaAsset;
import com.luciano.entity.StorageProviderConfig;
import com.luciano.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 媒体文件下载+存储服务
 * <p>
 * 下载远程文件 → 上传到当前 default StorageProvider → 更新 media_assets 记录
 * <p>
 * local 模式：文件存本地磁盘
 * s3/minio/tos 模式：文件上传到对象存储，URL 更新为公网地址
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaDownloadService {

    private final StorageProviderService providerService;
    private final StorageService storageService;

    /**
     * 下载远程文件并存储到当前 default provider。
     * <p>
     * - local 模式：下载到本地磁盘，返回本地相对路径
     * - s3 模式：下载后上传到对象存储，返回存储 key
     * <p>
     * 无论哪种模式，都会更新 asset 的 url/localPath/storageProviderId/storageKey。
     */
    public String downloadAndStore(MediaAsset asset) {
        if (asset == null || asset.getUrl() == null || asset.getUrl().isBlank()) {
            return null;
        }

        StorageProviderConfig provider = providerService.getDefault();
        String providerType = provider.getProviderType();

        // 已经是同一存储的，跳过
        if (asset.getStorageProviderId() != null && asset.getStorageProviderId().equals(provider.getId())) {
            if (providerType.equals("local") && asset.getLocalPath() != null) {
                Path existing = Paths.get(provider.getPath(), asset.getLocalPath());
                if (Files.exists(existing)) {
                    log.debug("[MediaDownload] Already stored in {}: {}", providerType, asset.getLocalPath());
                    return asset.getLocalPath();
                }
            }
            if (!providerType.equals("local") && asset.getStorageKey() != null) {
                log.debug("[MediaDownload] Already stored in {}: {}", providerType, asset.getStorageKey());
                return asset.getStorageKey();
            }
        }

        // 先下载到内存
        byte[] data;
        try {
            data = downloadToBytes(asset.getUrl(), asset.getMediaType());
        } catch (Exception e) {
            log.error("[MediaDownload] Failed to download id={}: {}", asset.getId(), e.getMessage());
            return null;
        }

        if (data == null || data.length == 0) {
            log.warn("[MediaDownload] Empty response for id={}", asset.getId());
            return null;
        }

        // 确定存储 key
        String ext = guessExtension(asset.getUrl(), asset.getMediaType());
        String fileName = asset.getId() + "_" + System.currentTimeMillis() / 1000 + ext;
        String storageKey = asset.getMediaType() + "/" + fileName;

        // 根据存储类型处理
        if ("local".equals(providerType)) {
            return storeLocal(asset, data, provider, storageKey);
        } else {
            // s3/minio/tos — 上传到对象存储
            return storeRemote(asset, data, provider, storageKey);
        }
    }

    /**
     * 旧方法兼容 — 调用新方法
     */
    public String downloadToLocal(MediaAsset asset) {
        return downloadAndStore(asset);
    }

    /**
     * 获取本地文件的绝对路径（仅 local 模式使用）
     */
    public Optional<Path> getLocalFile(String localPath) {
        if (localPath == null || localPath.isBlank()) return Optional.empty();
        StorageProviderConfig provider = providerService.getDefault();
        if ("local".equals(provider.getProviderType())) {
            Path file = Paths.get(provider.getPath(), localPath);
            return Files.exists(file) ? Optional.of(file) : Optional.empty();
        }
        return Optional.empty();
    }

    // ========== 私有方法 ==========

    private byte[] downloadToBytes(String url, String mediaType) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
        conn.setRequestProperty("Referer", "");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout("video".equalsIgnoreCase(mediaType) ? 120000 : 30000);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        if (code != 200) {
            log.warn("[MediaDownload] HTTP {} for url={}", code, url);
            return null;
        }

        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String storeLocal(MediaAsset asset, byte[] data, StorageProviderConfig provider, String storageKey) {
        try {
            Path targetDir = Paths.get(provider.getPath(), storageKey).getParent();
            Files.createDirectories(targetDir);
            Path targetFile = Paths.get(provider.getPath(), storageKey);
            Files.write(targetFile, data);

            log.info("[MediaDownload] Stored locally: id={}, size={}bytes, path={}", asset.getId(), data.length, storageKey);

            // 更新 asset
            asset.setLocalPath(storageKey);
            asset.setStorageProviderId(provider.getId());
            asset.setStorageKey(storageKey);
            // local 模式 URL 不变（指向 /api/v1/media/{id}/file）

            return storageKey;
        } catch (Exception e) {
            log.error("[MediaDownload] Local store failed id={}: {}", asset.getId(), e.getMessage());
            return null;
        }
    }

    private String storeRemote(MediaAsset asset, byte[] data, StorageProviderConfig provider, String storageKey) {
        try {
            String contentType = guessContentType(asset.getMediaType(), storageKey);
            String publicUrl = storageService.upload(provider.getId(), storageKey,
                    new ByteArrayInputStream(data), contentType);

            log.info("[MediaDownload] Stored remotely: id={}, size={}bytes, key={}, url={}",
                    asset.getId(), data.length, storageKey, publicUrl);

            // 更新 asset — URL 换成公网地址
            asset.setUrl(publicUrl);
            asset.setStorageProviderId(provider.getId());
            asset.setStorageKey(storageKey);
            // 清除 localPath（不再需要本地文件）
            asset.setLocalPath(null);

            return storageKey;
        } catch (Exception e) {
            log.error("[MediaDownload] Remote store failed id={}: {}", asset.getId(), e.getMessage());
            // 远程存储失败，回退到本地
            log.info("[MediaDownload] Falling back to local storage for id={}", asset.getId());
            return storeLocal(asset, data, getLocalFallbackProvider(), storageKey);
        }
    }

    /**
     * 获取本地回退 provider 配置
     * 如果有 local provider 用 local，否则临时构造一个
     */
    private StorageProviderConfig getLocalFallbackProvider() {
        StorageProviderConfig local = providerService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StorageProviderConfig>()
                        .eq(StorageProviderConfig::getProviderType, "local")
                        .eq(StorageProviderConfig::getEnabled, true)
                        .last("LIMIT 1"));
        if (local != null) return local;

        // 没有配置 local provider，用默认路径
        StorageProviderConfig fallback = new StorageProviderConfig();
        fallback.setId(0L);
        fallback.setProviderType("local");
        fallback.setConfig(new java.util.HashMap<>(java.util.Map.of(
                "path", "./uploads/media",
                "publicUrl", "http://localhost:8090"
        )));
        return fallback;
    }

    private String guessExtension(String url, String mediaType) {
        try {
            String path = URI.create(url).getPath();
            int queryIdx = path.indexOf(';');
            if (queryIdx > 0) path = path.substring(0, queryIdx);
            queryIdx = path.indexOf('?');
            if (queryIdx > 0) path = path.substring(0, queryIdx);

            if (path.endsWith(".png")) return ".png";
            if (path.endsWith(".gif")) return ".gif";
            if (path.endsWith(".webp")) return ".webp";
            if (path.endsWith(".mp4")) return ".mp4";
            if (path.endsWith(".webm")) return ".webm";
        } catch (Exception ignored) {
        }

        if ("video".equalsIgnoreCase(mediaType)) return ".mp4";
        return ".jpg";
    }

    private String guessContentType(String mediaType, String path) {
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".mp4")) return "video/mp4";
        if (path.endsWith(".webm")) return "video/webm";
        if ("video".equalsIgnoreCase(mediaType)) return "video/mp4";
        return "image/jpeg";
    }
}