package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 媒体生成结果
 * 适配器返回的统一结果格式。
 * 当一次生成产出多个结果时（如 n>1 或组图模式），
 * primary URL 放 originalUrl，其余放 additionalUrls。
 */
@Data
@Builder
public class MediaResult {

    /** 结果类型：video / image / audio */
    private String mediaType;

    /** 原始 URL（厂商 CDN） — 主结果 */
    private String originalUrl;

    /** 本地存储路径（下载后） — 主结果 */
    private String localPath;

    /** 额外结果 URL 列表（n>1 或组图模式时） */
    @Builder.Default
    private List<String> additionalUrls = java.util.Collections.emptyList();

    /** 时长（视频/音频，毫秒） */
    private Integer durationMs;

    /** 分辨率（如 "1920x1080"） */
    private String resolution;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 附加元数据（如种子值、模型版本等） */
    private Map<String, Object> metadata;
}