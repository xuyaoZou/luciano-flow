package com.luciano.adapter.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 提交生成任务响应
 */
@Data
@Builder
public class GenerateResponse {

    /** 平台任务 ID */
    private String taskId;

    /** 数据库 generation_tasks.id */
    private Long dbTaskId;

    /** 适配器 ID */
    private String adapterId;

    /** 能力标识 */
    private String capability;

    /** 厂商任务 ID */
    private String providerTaskId;

    /** 任务状态 */
    private String status;

    /** 费用预估 */
    private String costHint;
}