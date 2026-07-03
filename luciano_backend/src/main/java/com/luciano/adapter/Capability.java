package com.luciano.adapter;

/**
 * 模型能力枚举
 * 定义所有适配器可能支持的能力类型。
 * 每个适配器通过 getCapabilities() 声明自己支持哪些能力。
 */
public enum Capability {

    // ==================== 视频生成 ====================
    TEXT_TO_VIDEO("text_to_video", "文生视频", "视频"),
    IMAGE_TO_VIDEO("image_to_video", "图生视频", "视频"),
    FIRST_LAST_FRAME("first_last_frame", "首尾帧生成", "视频"),
    REFERENCE_TO_VIDEO("reference_to_video", "参考图生视频", "视频"),
    VIDEO_EXTEND("video_extend", "视频续写", "视频"),
    VIDEO_EDIT("video_edit", "视频编辑", "视频"),
    CAMERA_CONTROL("camera_control", "运镜控制", "视频"),
    LIP_SYNC("lip_sync", "对口型", "视频"),
    MOTION_CONTROL("motion_control", "运动控制", "视频"),
    MULTI_CHARACTER("multi_character", "多角色视频", "视频"),
    OMNI_VIDEO("omni_video", "视频Omni（多模态）", "视频"),

    // ==================== 图片生成 ====================
    TEXT_TO_IMAGE("text_to_image", "文生图", "图片"),
    OMNI_IMAGE("omni_image", "图像Omni（多模态）", "图片"),

    // ==================== 音频 ====================
    TEXT_TO_SPEECH("text_to_speech", "语音合成", "音频"),
    AUDIO_GENERATE("audio_generate", "音效/BGM 生成", "音频"),

    // ==================== Agent ====================
    AGENT_CHAT("agent_chat", "Agent 对话式创作", "Agent");

    private final String code;
    private final String displayName;
    private final String category;

    Capability(String code, String displayName, String category) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    /**
     * 从 code 字符串解析 Capability（用于 API 请求参数）
     */
    public static Capability fromCode(String code) {
        for (Capability cap : values()) {
            if (cap.code.equals(code)) {
                return cap;
            }
        }
        throw new IllegalArgumentException("Unknown capability code: " + code);
    }
}