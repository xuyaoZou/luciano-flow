package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "model_configs", autoResultMap = true)
public class ModelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String stepType;
    private Long providerId;
    private String modelName;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;
    private Boolean isDefault;
    private String providerSource;  // 'platform' or 'user'
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}