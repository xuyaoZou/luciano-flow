package com.luciano.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 端口数据类型枚举
 * <p>
 * 定义工作流节点间连线的类型约束。
 * 参考设计文档 §3.2 端口数据类型定义。
 * <p>
 * 每种类型有对应颜色，前端画布连线/端口用此颜色渲染。
 */
public enum PortType {

    // ==================== 媒体类型 ====================
    IMAGE("image", "图片", "#4A90D9"),
    VIDEO("video", "视频", "#D94A4A"),
    AUDIO("audio", "音频", "#4AD97A"),
    TEXT("text", "文本", "#D9C94A"),

    // ==================== 控制类型 ====================
    PROMPT("prompt", "提示词", "#D9A04A"),
    NEGATIVE_PROMPT("negative_prompt", "负向提示词", "#8B4513"),

    // ==================== 模型/参考类型 ====================
    MODEL("model", "模型", "#9B59B6"),
    ELEMENT("element", "主体", "#FF6B6B"),
    REFERENCE("reference", "参考素材", "#E91E63"),
    MASK("mask", "遮罩", "#2ECC71"),

    // ==================== 通用类型 ====================
    NUMBER("number", "数值", "#95A5A6"),
    ENUM("enum", "枚举", "#95A5A6"),

    // ==================== 未来扩展预留 ====================
    LATENT("latent", "潜空间", "#E8B4D9"),
    CONDITIONING("conditioning", "条件", "#FF8C00"),
    MESH("mesh", "3D网格", "#00FF7F"),
    ;

    private final String code;
    private final String displayName;
    private final String color;

    PortType(String code, String displayName, String color) {
        this.code = code;
        this.displayName = displayName;
        this.color = color;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    /**
     * 从 code 字符串解析 PortType
     */
    @JsonCreator
    public static PortType fromCode(String code) {
        for (PortType pt : values()) {
            if (pt.code.equals(code)) {
                return pt;
            }
        }
        throw new IllegalArgumentException("Unknown port type code: " + code);
    }
}