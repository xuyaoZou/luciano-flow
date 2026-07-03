package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "projects", autoResultMap = true)
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private String title;
    private String description;
    private String type;
    private String genre;
    private String ratio;
    private String visualStyle;
    private String status;
    private String thumbnail;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;
    private String modelProvider;
    private String contextSessionId;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String providerMeta;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;
}