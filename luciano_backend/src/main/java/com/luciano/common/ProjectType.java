package com.luciano.common;

/**
 * 创作类型枚举
 * 支持 Luciano 平台的多创作类型
 */
public enum ProjectType {
    SHORT_DRAMA("short_drama", "短剧"),
    CREATIVE_VIDEO("creative_video", "创意短视频"),
    PRODUCT_MARKETING("product_marketing", "产品营销");

    private final String code;
    private final String label;

    ProjectType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ProjectType fromCode(String code) {
        for (ProjectType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown project type: " + code);
    }
}