package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "style_presets", autoResultMap = true)
public class StylePreset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private String name;
    private String category;
    private String description;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;
    private String previewUrl;
    private Boolean isPublic;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tags;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;
}