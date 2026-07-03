package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * API 操作日志
 * 记录每次对外模型 API 调用的详情，用于排查、统计、成本核算
 */
@Data
@TableName("api_operation_log")
public class ApiOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    // ===== 谁 =====
    private Long userId;
    private String username;

    // ===== 做什么 =====
    private String adapterId;
    private String capability;
    private String operationType;    // submit / poll / download

    // ===== 关联 =====
    private String taskId;
    private String providerTaskId;
    private Long projectId;
    private Long episodeId;
    private Long storyboardId;

    // ===== API 调用详情 =====
    private String method;            // GET / POST
    private String path;
    private String requestBody;       // 请求参数（脱敏后）
    private Integer responseStatus;
    private String responseBody;       // 响应体（截断到 4KB）
    private String errorCode;
    private String errorMessage;

    // ===== 平台状态 =====
    private String platformStatus;    // 平台原始状态：queued/running/succeeded/succeed 等

    // ===== 结果 =====
    private Integer durationMs;
    private BigDecimal creditsUsed;
    private String resultUrl;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}