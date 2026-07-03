package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "generation_tasks", autoResultMap = true)
public class GenerationTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long episodeId;
    private Long storyboardId;
    private Long assetId;
    private Long userId;
    private String taskType;
    private String generationMode;
    private String provider;
    private String model;
    private String adapterId;
    private String capability;
    private String prompt;
    private String negativePrompt;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String referenceUrls;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;
    private String outputUrl;
    private String outputPath;
    private Integer durationMs;
    private String status;
    private String taskId;
    private String providerSource;
    private String threadId;
    private String errorMsg;
    private Integer creditsCost;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
}