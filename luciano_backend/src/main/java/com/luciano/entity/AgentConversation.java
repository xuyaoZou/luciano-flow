package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "agent_conversations", autoResultMap = true)
public class AgentConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    private String provider;
    private String contextSessionId;
    private String status;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String providerMeta;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;
    private String title;
}