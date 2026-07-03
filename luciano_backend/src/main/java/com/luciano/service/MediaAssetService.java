package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.*;
import com.luciano.repository.mapper.CharacterAssetMapper;
import com.luciano.repository.mapper.PropAssetMapper;
import com.luciano.repository.mapper.SceneAssetMapper;
import com.luciano.repository.mapper.StoryboardMapper;
import com.luciano.entity.MediaAsset;
import com.luciano.repository.mapper.MediaAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAssetService extends ServiceImpl<MediaAssetMapper, MediaAsset> {

    private final CharacterAssetMapper characterAssetMapper;
    private final SceneAssetMapper sceneAssetMapper;
    private final PropAssetMapper propAssetMapper;
    private final StoryboardMapper storyboardMapper;

    /**
     * 添加媒体资产
     */
    @Transactional
    public MediaAsset addAsset(Long projectId, Long userId, Long conversationId,
                               String source, String mediaType, String url,
                               String thumbnailUrl, String metadata, String runId, Long agentMessageId) {
        MediaAsset asset = new MediaAsset();
        asset.setProjectId(projectId);
        asset.setUserId(userId);
        asset.setConversationId(conversationId);
        asset.setSource(source);
        asset.setMediaType(mediaType);
        asset.setUrl(url);
        asset.setThumbnailUrl(thumbnailUrl);
        asset.setMetadata(metadata != null ? metadata : "{}");
        asset.setRunId(runId);
        asset.setAgentMessageId(agentMessageId);
        save(asset);
        log.info("[MediaAsset] Added id={}, project={}, type={}, source={}, runId={}, msgId={}",
                asset.getId(), projectId, mediaType, source, runId, agentMessageId);
        return asset;
    }

    /**
     * 关联媒体资产到专业模式资产
     * 同时更新专业模式资产的 imageUrl/videoUrl 为媒体资产的 URL
     *
     * @param mediaAssetId 媒体资产 ID
     * @param assetType    专业模式资产类型: character / scene / prop / storyboard_first_frame / storyboard_last_frame / storyboard_video
     * @param assetId      专业模式资产 ID
     */
    @Transactional
    public void linkToProfessionalAsset(Long mediaAssetId, String assetType, Long assetId) {
        MediaAsset mediaAsset = getById(mediaAssetId);
        if (mediaAsset == null) {
            throw new IllegalArgumentException("媒体资产不存在: " + mediaAssetId);
        }
        String url = mediaAsset.getUrl();

        switch (assetType) {
            case "character" -> {
                CharacterAsset ca = characterAssetMapper.selectById(assetId);
                if (ca == null) throw new IllegalArgumentException("角色不存在: " + assetId);
                ca.setMediaAssetId(mediaAssetId);
                ca.setImageUrl(url);
                characterAssetMapper.updateById(ca);
                log.info("[MediaAsset] Linked id={} → character id={}, url={}", mediaAssetId, assetId, url.substring(0, Math.min(60, url.length())));
            }
            case "scene" -> {
                SceneAsset sa = sceneAssetMapper.selectById(assetId);
                if (sa == null) throw new IllegalArgumentException("场景不存在: " + assetId);
                sa.setMediaAssetId(mediaAssetId);
                sa.setImageUrl(url);
                sceneAssetMapper.updateById(sa);
                log.info("[MediaAsset] Linked id={} → scene id={}, url={}", mediaAssetId, assetId, url.substring(0, Math.min(60, url.length())));
            }
            case "prop" -> {
                PropAsset pa = propAssetMapper.selectById(assetId);
                if (pa == null) throw new IllegalArgumentException("道具不存在: " + assetId);
                pa.setMediaAssetId(mediaAssetId);
                pa.setImageUrl(url);
                propAssetMapper.updateById(pa);
                log.info("[MediaAsset] Linked id={} → prop id={}, url={}", mediaAssetId, assetId, url.substring(0, Math.min(60, url.length())));
            }
            case "storyboard_first_frame" -> {
                Storyboard sb = storyboardMapper.selectById(assetId);
                if (sb == null) throw new IllegalArgumentException("分镜不存在: " + assetId);
                sb.setFirstFrameMediaId(mediaAssetId);
                sb.setFirstFrameImageUrl(url);
                storyboardMapper.updateById(sb);
                log.info("[MediaAsset] Linked id={} → storyboard id={} firstFrame", mediaAssetId, assetId);
            }
            case "storyboard_last_frame" -> {
                Storyboard sb = storyboardMapper.selectById(assetId);
                if (sb == null) throw new IllegalArgumentException("分镜不存在: " + assetId);
                sb.setLastFrameMediaId(mediaAssetId);
                sb.setLastFrameImageUrl(url);
                storyboardMapper.updateById(sb);
                log.info("[MediaAsset] Linked id={} → storyboard id={} lastFrame", mediaAssetId, assetId);
            }
            case "storyboard_video" -> {
                Storyboard sb = storyboardMapper.selectById(assetId);
                if (sb == null) throw new IllegalArgumentException("分镜不存在: " + assetId);
                sb.setVideoMediaId(mediaAssetId);
                sb.setVideoUrl(url);
                storyboardMapper.updateById(sb);
                log.info("[MediaAsset] Linked id={} → storyboard id={} video", mediaAssetId, assetId);
            }
            default -> throw new IllegalArgumentException("不支持的资产类型: " + assetType);
        }
    }

    /**
     * 取消关联 — 清除专业模式资产的 media_asset_id，保留 imageUrl（用户可能手动上传了替代图）
     */
    @Transactional
    public void unlinkFromProfessionalAsset(String assetType, Long assetId) {
        switch (assetType) {
            case "character" -> {
                LambdaUpdateWrapper<CharacterAsset> w = new LambdaUpdateWrapper<>();
                w.eq(CharacterAsset::getId, assetId).set(CharacterAsset::getMediaAssetId, null);
                characterAssetMapper.update(null, w);
            }
            case "scene" -> {
                LambdaUpdateWrapper<SceneAsset> w = new LambdaUpdateWrapper<>();
                w.eq(SceneAsset::getId, assetId).set(SceneAsset::getMediaAssetId, null);
                sceneAssetMapper.update(null, w);
            }
            case "prop" -> {
                LambdaUpdateWrapper<PropAsset> w = new LambdaUpdateWrapper<>();
                w.eq(PropAsset::getId, assetId).set(PropAsset::getMediaAssetId, null);
                propAssetMapper.update(null, w);
            }
            case "storyboard_first_frame" -> {
                LambdaUpdateWrapper<Storyboard> w = new LambdaUpdateWrapper<>();
                w.eq(Storyboard::getId, assetId).set(Storyboard::getFirstFrameMediaId, null);
                storyboardMapper.update(null, w);
            }
            case "storyboard_last_frame" -> {
                LambdaUpdateWrapper<Storyboard> w = new LambdaUpdateWrapper<>();
                w.eq(Storyboard::getId, assetId).set(Storyboard::getLastFrameMediaId, null);
                storyboardMapper.update(null, w);
            }
            case "storyboard_video" -> {
                LambdaUpdateWrapper<Storyboard> w = new LambdaUpdateWrapper<>();
                w.eq(Storyboard::getId, assetId).set(Storyboard::getVideoMediaId, null);
                storyboardMapper.update(null, w);
            }
            default -> throw new IllegalArgumentException("不支持的资产类型: " + assetType);
        }
        log.info("[MediaAsset] Unlinked {} id={}", assetType, assetId);
    }

    /**
     * 查询项目的所有媒体资产
     */
    public List<MediaAsset> listByProjectId(Long projectId) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getProjectId, projectId)
               .orderByDesc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }

    public List<MediaAsset> listByUserId(Long userId) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getUserId, userId)
               .orderByDesc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }

    public List<MediaAsset> listByUserIdAndType(Long userId, String mediaType) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getUserId, userId)
               .eq(MediaAsset::getMediaType, mediaType)
               .orderByDesc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }

    public List<MediaAsset> listByProjectIdAndType(Long projectId, String mediaType) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getProjectId, projectId)
               .eq(MediaAsset::getMediaType, mediaType)
               .orderByDesc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }

    public List<MediaAsset> listByProjectIdAndSource(Long projectId, String source) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getProjectId, projectId)
               .eq(MediaAsset::getSource, source)
               .orderByDesc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }

    public List<MediaAsset> listByConversationId(Long conversationId) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getConversationId, conversationId)
               .orderByAsc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }

    public List<MediaAsset> listByAgentMessageId(Long agentMessageId) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getAgentMessageId, agentMessageId)
               .orderByAsc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }

    public List<MediaAsset> listByConversationIdAndRunId(Long conversationId, String runId) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getConversationId, conversationId)
               .eq(MediaAsset::getRunId, runId)
               .orderByAsc(MediaAsset::getCreatedAt);
        return list(wrapper);
    }
}