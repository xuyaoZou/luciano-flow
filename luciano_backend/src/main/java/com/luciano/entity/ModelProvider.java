package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "model_providers", autoResultMap = true)
public class ModelProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String displayName;
    private String serviceType;
    private String baseUrl;
    private String apiKey;
    private String apiSecret;
    private Boolean isActive;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}