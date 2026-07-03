package com.luciano.controller;

import com.luciano.entity.MediaAsset;
import com.luciano.entity.StorageProviderConfig;
import com.luciano.service.MediaAssetService;
import com.luciano.service.StorageProviderService;
import com.luciano.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传 API
 * <p>
 * 供创作工作台等场景上传参考图片。
 * 上传 → 存储到配置的 StorageProvider → 创建 media_assets 记录 → 返回公网 URL。
 * <p>
 * Local 模式：文件存本地，URL 指向 /api/v1/media/{id}/file
 * S3/OSS/TOS 模式：文件存对象存储，URL 指向 CDN
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final MediaAssetService mediaAssetService;
    private final StorageService storageService;
    private final StorageProviderService providerService;

    /**
     * 上传图片文件
     * <p>
     * 返回格式：{ id, url, publicUrl, storageKey, mediaType }
     * url 是本地媒体服务路径（/api/v1/media/{id}/file）
     * publicUrl 是公网可访问的 URL（供外部 API 调用）
     */
    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "source", defaultValue = "upload") String source) {

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "仅支持图片文件",
                    "receivedType", contentType != null ? contentType : "unknown"
            ));
        }

        // 校验文件大小（最大 20MB）
        if (file.getSize() > 20 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "文件大小不能超过 20MB",
                    "receivedSize", file.getSize()
            ));
        }

        // 获取当前存储配置
        StorageProviderConfig provider = providerService.getDefault();

        // 确定扩展名和存储 key
        String ext = guessImageExt(contentType, file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        String storageKey = "image/upload/" + fileName;

        try {
            // 上传到存储
            String publicUrl = storageService.upload(provider.getId(), storageKey, file.getInputStream(), contentType);

            // 创建 media_assets 记录
            MediaAsset asset = mediaAssetService.addAsset(
                    projectId != null ? projectId : 0L,
                    0L,  // userId — TODO: 从 JWT 获取
                    null, // conversationId
                    source,
                    "image",
                    publicUrl,  // 公网 URL（Kling API 可用）
                    null, // thumbnailUrl
                    null, // metadata
                    null, // runId
                    null  // agentMessageId
            );

            // 更新 storage 关联
            asset.setStorageProviderId(provider.getId());
            asset.setStorageKey(storageKey);
            // Local 模式：localPath 用存储 key
            if ("local".equals(provider.getProviderType())) {
                asset.setLocalPath(storageKey);
            }
            mediaAssetService.updateById(asset);

            log.info("[Upload] Image uploaded: assetId={}, provider={}, storageKey={}, publicUrl={}",
                    asset.getId(), provider.getProviderType(), storageKey, publicUrl);

            Map result = new HashMap();
            result.put("id", asset.getId());
            result.put("url", "/api/v1/media/" + asset.getId() + "/file");
            result.put("publicUrl", publicUrl);
            result.put("storageKey", storageKey);
            if (asset.getLocalPath() != null) result.put("localPath", asset.getLocalPath());
            result.put("mediaType", "image");
            result.put("fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
            result.put("size", file.getSize());
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("[Upload] Failed to upload image: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "文件保存失败: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[Upload] Storage error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "存储失败: " + e.getMessage()));
        }
    }

    private String guessImageExt(String contentType, String originalFilename) {
        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("webp")) return ".webp";
        }
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".png")) return ".png";
            if (lower.endsWith(".gif")) return ".gif";
            if (lower.endsWith(".webp")) return ".webp";
        }
        return ".jpg";
    }
}