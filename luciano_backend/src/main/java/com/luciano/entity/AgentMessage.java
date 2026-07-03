package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * Agent 对话消息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("agent_messages")
public class AgentMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant */
    private String role;

    /** 用户发送的文本 */
    private String content;

    /** Agent 回复的文本 */
    private String text;

    /** 小云雀 run_id */
    private String runId;

    /** processing / completed / failed */
    private String status;

    private String errorMsg;

    /** 本条消息产出的媒体数量 */
    private Integer mediaCount;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}