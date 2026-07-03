package com.luciano.flow;

/**
 * 特殊节点类型常量
 * <p>
 * 不对应任何 Capability，不经过适配器。
 * 参考设计文档 §3.4 特殊节点定义。
 */
public final class NodeType {

    private NodeType() {
        // 常量类不允许实例化
    }

    // ==================== 输入节点 ====================
    /** 图片上传/选择 */
    public static final String IMAGE_INPUT = "ImageInput";
    /** 视频上传/选择 */
    public static final String VIDEO_INPUT = "VideoInput";
    /** 音频上传/选择 */
    public static final String AUDIO_INPUT = "AudioInput";
    /** 文本/提示词输入 */
    public static final String TEXT_INPUT = "TextInput";

    // ==================== 预览节点 ====================
    /** 图片预览/下载 */
    public static final String IMAGE_PREVIEW = "ImagePreview";
    /** 视频预览/下载 */
    public static final String VIDEO_PREVIEW = "VideoPreview";

    // ==================== 控制节点 ====================
    /** 条件分支 */
    public static final String SWITCH = "Switch";

    // ==================== 主体节点 ====================
    /** Kling 主体源（创建/引用主体，输出 element_id） */
    public static final String ELEMENT_SOURCE = "ElementSource";

    /**
     * 判断是否为特殊节点（非 Capability 节点）
     */
    public static boolean isSpecialNode(String type) {
        return IMAGE_INPUT.equals(type)
                || VIDEO_INPUT.equals(type)
                || AUDIO_INPUT.equals(type)
                || TEXT_INPUT.equals(type)
                || IMAGE_PREVIEW.equals(type)
                || VIDEO_PREVIEW.equals(type)
                || SWITCH.equals(type)
                || ELEMENT_SOURCE.equals(type);
    }

    /**
     * 判断是否为输入类节点（只有输出端口，没有输入端口）
     */
    public static boolean isInputNode(String type) {
        return IMAGE_INPUT.equals(type)
                || VIDEO_INPUT.equals(type)
                || AUDIO_INPUT.equals(type)
                || TEXT_INPUT.equals(type)
                || ELEMENT_SOURCE.equals(type);
    }

    /**
     * 判断是否为输出/预览类节点（只有输入端口，没有输出端口）
     */
    public static boolean isOutputNode(String type) {
        return IMAGE_PREVIEW.equals(type)
                || VIDEO_PREVIEW.equals(type);
    }
}