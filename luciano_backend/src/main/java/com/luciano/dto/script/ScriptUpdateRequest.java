package com.luciano.dto.script;

import lombok.Data;

/**
 * 更新剧本大纲请求（部分更新）
 */
@Data
public class ScriptUpdateRequest {

    /** 原始创意 */
    private String originalIdea;

    /** 剧本摘要 */
    private String summary;

    /** 完整剧本文本 */
    private String fullScript;

    /** 生成模式 */
    private String generationMode;

    /** 状态 */
    private String status;
}