package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 剧本大纲
 * 三级结构：originalIdea → summary → fullScript
 * 支持手动编辑和 Agent 自动生成
 */
@Data
@TableName("scripts")
public class Script {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 原始创意（一句话/一段话） */
    private String originalIdea;

    /** 剧本摘要（几百字） */
    private String summary;

    /** 完整剧本文本 */
    private String fullScript;

    /** 生成模式：manual（手动）/ agent（Agent 自动） */
    private String generationMode;

    /** 来源：user（用户提交）/ agent（Agent 生成） */
    private String source;

    /** 状态：draft / generating / completed / failed */
    private String status;

    /** Agent 会话 ID（复用会话） */
    private String agentThreadId;

    /** Agent 运行 ID（当前轮次） */
    private String agentRunId;

    /** 版本号 */
    private Integer version;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}