package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import com.luciano.config.StringArrayTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "media_assets", autoResultMap = true)
public class MediaAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    private Long conversationId;
    private String source;
    private String mediaType;
    private String url;
    private String thumbnailUrl;
    private String localPath;
    private String runId;
    private Long agentMessageId;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;
    private OffsetDateTime createdAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;

    /** 存储提供者 ID（关联 storage_providers 表） */
    private Long storageProviderId;

    /** 对象存储 key（local=本地相对路径，s3=对象key） */
    private String storageKey;
}