package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "prop_assets", autoResultMap = true)
public class PropAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private Long projectId;
    private String name;
    private String propRef;
    private String description;
    private String material;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String visualAttributes;
    private String imageUrl;
    private Long mediaAssetId;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String referenceImages;
    private Boolean isPublic;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tags;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;
}