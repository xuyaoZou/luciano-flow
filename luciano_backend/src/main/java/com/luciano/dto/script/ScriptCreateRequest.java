package com.luciano.dto.script;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/生成剧本大纲请求
 */
@Data
public class ScriptCreateRequest {

    /** 原始创意 */
    @NotBlank(message = "原始创意不能为空")
    private String originalIdea;

    /** 生成模式：agent（自动生成）/ manual（手动，默认） */
    private String generationMode = "agent";
}