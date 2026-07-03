package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 任务句柄
 * 提交任务后返回的标识，用于后续轮询和取消。
 */
@Data
@Builder
public class TaskHandle {

    /** 全局唯一任务 ID（平台生成） */
    private String taskId;

    /** 适配器 ID（如 "kling", "seedance"） */
    private String adapterId;

    /** 能力类型 */
    private Capability capability;

    /** 厂商返回的任务 ID（用于轮询） */
    private String providerTaskId;

    /** 厂商返回的会话 ID（如小云雀的 threadId） */
    private String providerThreadId;

    /** 同步返回的结果 URL（如 Seedream 图片生成直接返回） */
    private String resultUrl;

    /** 同步返回的多结果 URL 列表（组图模式） */
    private java.util.List<String> resultUrls;

    /** 轮询路径（提交时确定，避免轮询时重新推断） */
    private String pollPath;

    /** 平台原始状态（如 queued/running/succeeded/succeed 等）— poll 时回填 */
    private String platformStatus;

    /** 任务创建时间 */
    private OffsetDateTime createdAt;

    /** 预计完成时间 */
    private OffsetDateTime estimatedCompletedAt;
}