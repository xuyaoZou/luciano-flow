package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "character_assets", autoResultMap = true)
public class CharacterAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private Long projectId;
    private String name;
    private String roleRef;
    private String description;
    private String appearance;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String visualAttributes;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String vocalAttributes;
    private String personality;
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