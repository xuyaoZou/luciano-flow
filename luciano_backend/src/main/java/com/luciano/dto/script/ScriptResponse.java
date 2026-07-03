package com.luciano.dto.script;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 剧本大纲响应
 */
@Data
@Builder
public class ScriptResponse {

    private Long id;
    private Long projectId;
    private String originalIdea;
    private String summary;
    private String fullScript;
    private String generationMode;
    private String source;
    private String status;
    private String agentThreadId;
    private String agentRunId;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}