package com.luciano.adapter.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 任务状态查询响应
 */
@Data
@Builder
public class TaskStatusResponse {

    /** 平台任务 ID */
    private String taskId;

    /** 数据库 generation_tasks.id */
    private Long dbTaskId;

    /** 适配器 ID */
    private String adapterId;

    /** 任务状态 */
    private String status;

    /** 结果 URL（COMPLETED 时） */
    private String resultUrl;

    /** 本地存储路径（COMPLETED 时） */
    private String localPath;

    /** media_assets.id（COMPLETED 时） */
    private Long mediaAssetId;

    /** 时长（毫秒，视频/音频） */
    private Integer durationMs;

    /** 分辨率 */
    private String resolution;

    /** 尾帧 URL（return_last_frame=true 时返回） */
    private String lastFrameUrl;

    /** 错误信息（FAILED 时） */
    private String errorMsg;
}