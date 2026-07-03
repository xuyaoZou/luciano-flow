package com.luciano.adapter;

import lombok.Data;

import java.util.List;

/**
 * 参数类型枚举
 * 定义前端动态表单可渲染的参数类型。
 * 对应 ComfyUI 的 IO 枚举。
 */
public enum ParamType {

    STRING("string", "文本"),
    INTEGER("integer", "整数"),
    FLOAT("float", "浮点数"),
    BOOLEAN("boolean", "布尔值"),
    ENUM("enum", "枚举选择"),
    IMAGE("image", "图片"),
    VIDEO("video", "视频"),
    AUDIO("audio", "音频"),
    IMAGE_LIST("image_list", "图片列表"),
    VIDEO_LIST("video_list", "视频列表"),
    AUDIO_LIST("audio_list", "音频列表"),
    ELEMENT_LIST("element_list", "主体引用列表");

    private final String code;
    private final String displayName;

    ParamType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }
}