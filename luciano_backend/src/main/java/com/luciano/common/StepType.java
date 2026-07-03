package com.luciano.common;

/**
 * 生成步骤类型枚举
 * 用于步骤级模型配置
 */
public enum StepType {
    CHARACTER_IMAGE("character_image", "角色图"),
    SCENE_IMAGE("scene_image", "场景图"),
    PROP_IMAGE("prop_image", "道具图"),
    VIDEO("video", "视频"),
    TTS("tts", "语音合成"),
    COMPOSE("compose", "合成"),
    SCRIPT_GENERATION("script_generation", "剧本大纲生成");

    private final String code;
    private final String label;

    StepType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
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