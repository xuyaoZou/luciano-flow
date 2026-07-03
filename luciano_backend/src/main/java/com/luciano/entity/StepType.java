package com.luciano.entity;

/**
 * 步骤类型枚举
 * 每个步骤对应一种生成任务
 */
public enum StepType {
    CHARACTER_IMAGE("character_image", "角色图片生成"),
    SCENE_IMAGE("scene_image", "场景图片生成"),
    PROP_IMAGE("prop_image", "道具图片生成"),
    VIDEO("video", "视频生成"),
    TTS("tts", "语音合成"),
    SCRIPT_GENERATION("script_generation", "剧本大纲生成"),
    FIRST_FRAME("first_frame", "首帧图片生成"),
    LAST_FRAME("last_frame", "尾帧图片生成");

    private final String code;
    private final String description;

    StepType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static StepType fromCode(String code) {
        for (StepType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown step type: " + code);
    }
}