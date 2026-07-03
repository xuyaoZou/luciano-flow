package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "storyboards", autoResultMap = true)
public class Storyboard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long episodeId;
    private Integer storyboardNumber;
    private String shotId;
    private String description;
    private String rawDescription;
    private Integer durationMs;
    private String generationMode;
    private String expressPrompt;
    private String sceneDescription;
    private String atmosphere;
    private String timeOfDay;
    private String dialogue;
    private String voiceover;
    private String composedImageUrl;
    private String firstFrameImageUrl;
    private String lastFrameImageUrl;
    private String videoUrl;
    private Long firstFrameMediaId;
    private Long lastFrameMediaId;
    private Long videoMediaId;
    private String ttsAudioUrl;
    private String subtitleUrl;
    private String composedVideoUrl;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tagMap;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;
}