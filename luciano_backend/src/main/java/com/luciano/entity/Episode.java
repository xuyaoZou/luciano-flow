package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "episodes", autoResultMap = true)
public class Episode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer episodeNumber;
    private String title;
    private String synopsis;
    private Integer duration;
    private String status;
    private String xyqThreadId;
    private String bgmUrl;
    private String bgmType;
    private Float bgmVolume;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;
}