package com.luciano.controller;

import com.luciano.entity.MediaAsset;
import com.luciano.entity.StorageProviderConfig;
import com.luciano.service.MediaAssetService;
import com.luciano.service.MediaDownloadService;
import com.luciano.service.StorageProviderService;
import com.luciano.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaFileController {

    private final MediaAssetService mediaAssetService;
    private final MediaDownloadService mediaDownloadService;
    private final StorageProviderService providerService;
    private final StorageService storageService;

    /**
     * 提供媒体文件
     * <p>
     * 对于前端 useMediaLoader 的请求（带 Authorization），不返回 302 重定向，
     * 因为 fetch 跟随重定向会丢失 Authorization header。
     * 改为代理下载文件内容直接返回。
     * <p>
     * 对象存储的文件通过流式代理，本地文件直接返回。
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<?> getMediaFile(@PathVariable Long id) {
        MediaAsset asset = mediaAssetService.getById(id);
        if (asset == null) {
            return ResponseEntity.notFound().build();
        }

        // 1. 对象存储文件：代理下载，直接返回内容（不 302 重定向）
        if (asset.getStorageProviderId() != null && asset.getStorageKey() != null) {
            StorageProviderConfig provider = providerService.getById(asset.getStorageProviderId());
            if (provider != null && !"local".equals(provider.getProviderType())) {
                try {
                    String publicUrl = storageService.getPublicUrl(provider.getId(), asset.getStorageKey());
                    log.debug("[MediaFile] Proxying object storage file id={}, url={}", id, publicUrl);
                    Resource resource = storageService.downloadAsResource(provider.getId(), asset.getStorageKey(), asset.getStorageKey());
                    String contentType = guessContentType(asset.getMediaType(), asset.getStorageKey());
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                            .body(resource);
                } catch (Exception e) {
                    log.warn("[MediaFile] Failed to proxy object storage file id={}: {}", id, e.getMessage());
                    // 回退到 302 重定向
                    if (asset.getUrl() != null && !asset.getUrl().startsWith("/api/v1/")) {
                        return ResponseEntity.status(302)
                                .header(HttpHeaders.LOCATION, asset.getUrl())
                                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                                .build();
                    }
                }
            }
        }

        // 2. 本地文件：直接返回
        if (asset.getLocalPath() != null && !asset.getLocalPath().isBlank()) {
            Optional<Path> localFile = mediaDownloadService.getLocalFile(asset.getLocalPath());
            if (localFile.isPresent()) {
                Resource resource = new FileSystemResource(localFile.get());
                String contentType = guessContentType(asset.getMediaType(), localFile.get().toString());
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                        .body(resource);
            }
        }

        // 3. 尝试下载并存储
        try {
            String result = mediaDownloadService.downloadAndStore(asset);
            if (result != null) {
                mediaAssetService.updateById(asset);

                // 对象存储 → 代理返回
                if (asset.getStorageProviderId() != null) {
                    StorageProviderConfig provider = providerService.getById(asset.getStorageProviderId());
                    if (provider != null && !"local".equals(provider.getProviderType())) {
                        Resource resource = storageService.downloadAsResource(provider.getId(), asset.getStorageKey(), asset.getStorageKey());
                        String contentType = guessContentType(asset.getMediaType(), asset.getStorageKey());
                        return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(contentType))
                                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                                .body(resource);
                    }
                }

                // 本地文件 → 直接返回
                Optional<Path> localFile = mediaDownloadService.getLocalFile(result);
                if (localFile.isPresent()) {
                    Resource resource = new FileSystemResource(localFile.get());
                    String contentType = guessContentType(asset.getMediaType(), localFile.get().toString());
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                            .body(resource);
                }
            }
        } catch (Exception e) {
            log.warn("[MediaFile] Failed to download on-demand id={}: {}", id, e.getMessage());
        }

        // 最终回退：302 重定向到原始 URL
        if (asset.getUrl() != null) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, asset.getUrl())
                    .build();
        }

        return ResponseEntity.notFound().build();
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