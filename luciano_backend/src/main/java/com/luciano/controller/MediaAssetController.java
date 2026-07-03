package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.MediaAsset;
import com.luciano.service.MediaAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统一资源库控制器
 * 两种创作模式的媒体文件都进这里
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media-assets")
@RequiredArgsConstructor
public class MediaAssetController {

    private final MediaAssetService mediaAssetService;

    /**
     * 查询项目的资源库
     * GET /api/v1/media-assets?projectId=5&mediaType=image&source=agent
     */
    @GetMapping
    public Result<List<MediaAsset>> listAssets(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) String source) {

        List<MediaAsset> assets;
        if (projectId != null) {
            // 按项目查询
            if (mediaType != null) {
                assets = mediaAssetService.listByProjectIdAndType(projectId, mediaType);
            } else if (source != null) {
                assets = mediaAssetService.listByProjectIdAndSource(projectId, source);
            } else {
                assets = mediaAssetService.listByProjectId(projectId);
            }
        } else {
            // 不指定项目：查当前用户所有资产
            if (mediaType != null) {
                assets = mediaAssetService.listByUserIdAndType(userId, mediaType);
            } else {
                assets = mediaAssetService.listByUserId(userId);
            }
        }
        return Result.ok(assets);
    }

    /**
     * 获取单个资源详情
     * GET /api/v1/media-assets/{id}
     */
    @GetMapping("/{id}")
    public Result<MediaAsset> getAsset(@PathVariable Long id) {
        MediaAsset asset = mediaAssetService.getById(id);
        return Result.ok(asset);
    }

    /**
     * 关联资源到专业模式资产
     * POST /api/v1/media-assets/{id}/link
     * body: { "assetType": "character", "assetId": 4 }
     *
     * assetType 支持: character / scene / prop / storyboard_first_frame / storyboard_last_frame / storyboard_video
     */
    @PostMapping("/{id}/link")
    public Result<Void> linkToProfessionalAsset(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String assetType = (String) body.get("assetType");
        Long assetId = Long.valueOf(body.get("assetId").toString());

        mediaAssetService.linkToProfessionalAsset(id, assetType, assetId);
        return Result.ok();
    }

    /**
     * 取消关联
     * DELETE /api/v1/media-assets/{id}/link?assetType=character&assetId=4
     */
    @DeleteMapping("/{id}/link")
    public Result<Void> unlinkFromProfessionalAsset(
            @PathVariable Long id,
            @RequestParam String assetType,
            @RequestParam Long assetId) {
        mediaAssetService.unlinkFromProfessionalAsset(assetType, assetId);
        return Result.ok();
    }

    /**
     * 删除资源
     * DELETE /api/v1/media-assets/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAsset(@PathVariable Long id) {
        mediaAssetService.removeById(id);
        log.info("[MediaAsset] Deleted id={}", id);
        return Result.ok();
    }
}