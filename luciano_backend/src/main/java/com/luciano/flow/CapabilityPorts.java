package com.luciano.flow;

import com.luciano.adapter.Capability;

import java.util.List;
import java.util.Map;

/**
 * 能力端口声明
 * <p>
 * 定义每个 Capability 节点的输入/输出端口。
 * 参考设计文档 §3.4 每个 Capability 的端口声明。
 * <p>
 * 这个类用于：
 * 1. 前端创建 Capability 节点时自动填充 inputSlots/outputSlots
 * 2. 工作流引擎执行时校验参数完整性
 * 3. 连线时校验类型兼容性
 */
public final class CapabilityPorts {

    private CapabilityPorts() {
        // 常量类不允许实例化
    }

    // ==================== 通用端口定义 ====================

    private static final PortDef PROMPT_INPUT = PortDef.builder()
            .name("prompt").displayName("提示词").dataType(PortType.PROMPT).required(false).build();

    private static final PortDef PROMPT_REQUIRED = PortDef.builder()
            .name("prompt").displayName("提示词").dataType(PortType.PROMPT).required(true).build();

    private static final PortDef NEGATIVE_PROMPT_INPUT = PortDef.builder()
            .name("negative_prompt").displayName("负向提示词").dataType(PortType.NEGATIVE_PROMPT).required(false).build();

    private static final PortDef IMAGE_INPUT = PortDef.builder()
            .name("image").displayName("图片").dataType(PortType.IMAGE).required(true).build();

    private static final PortDef VIDEO_INPUT = PortDef.builder()
            .name("video").displayName("视频").dataType(PortType.VIDEO).required(true).build();

    private static final PortDef AUDIO_INPUT = PortDef.builder()
            .name("audio").displayName("音频").dataType(PortType.AUDIO).required(false).build();

    private static final PortDef REFERENCE_INPUT = PortDef.builder()
            .name("reference").displayName("参考素材").dataType(PortType.REFERENCE).required(true).build();

    private static final PortDef FIRST_FRAME_INPUT = PortDef.builder()
            .name("first_frame").displayName("首帧图片").dataType(PortType.IMAGE).required(true).build();

    private static final PortDef LAST_FRAME_INPUT = PortDef.builder()
            .name("last_frame").displayName("尾帧图片").dataType(PortType.IMAGE).required(false).build();

    private static final PortDef VIDEO_OUTPUT = PortDef.builder()
            .name("video").displayName("视频").dataType(PortType.VIDEO).required(false).build();

    private static final PortDef IMAGE_OUTPUT = PortDef.builder()
            .name("image").displayName("图片").dataType(PortType.IMAGE).required(false).build();

    private static final PortDef AUDIO_OUTPUT = PortDef.builder()
            .name("audio").displayName("音频").dataType(PortType.AUDIO).required(false).build();

    // ==================== 视频生成类 ====================

    /** 文生视频：PROMPT(必填) → VIDEO */
    public static final List<PortDef> T2V_INPUTS = List.of(PROMPT_REQUIRED, NEGATIVE_PROMPT_INPUT);
    public static final List<PortDef> T2V_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 图生视频：IMAGE(必填) + PROMPT(可选) → VIDEO */
    // I2V 使用 multi 端口，支持多图连线（Seedance 2.0 多模态参考）
    private static final PortDef IMAGE_INPUT_MULTI = PortDef.builder()
            .name("image").displayName("图片").dataType(PortType.IMAGE).required(true).multi(true).build();
    public static final List<PortDef> I2V_INPUTS = List.of(IMAGE_INPUT_MULTI, PROMPT_INPUT);
    public static final List<PortDef> I2V_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 首尾帧：FIRST_FRAME(必填) + LAST_FRAME(可选) + PROMPT(可选) → VIDEO */
    public static final List<PortDef> FLF_INPUTS = List.of(FIRST_FRAME_INPUT, LAST_FRAME_INPUT, PROMPT_INPUT);
    public static final List<PortDef> FLF_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 参考图生视频：REFERENCE(必填) + PROMPT(可选) → VIDEO */
    /** 参考图生视频：REFERENCE_IMAGES(multi) + REFERENCE_VIDEOS(multi) + REFERENCE_AUDIOS(multi) + PROMPT → VIDEO */
    private static final PortDef REF_IMAGES_INPUT = PortDef.builder()
            .name("reference_images").displayName("参考图片").dataType(PortType.IMAGE).required(false).multi(true).build();
    private static final PortDef REF_VIDEOS_INPUT = PortDef.builder()
            .name("reference_videos").displayName("参考视频").dataType(PortType.VIDEO).required(false).multi(true).build();
    private static final PortDef REF_AUDIOS_INPUT = PortDef.builder()
            .name("reference_audios").displayName("参考音频").dataType(PortType.AUDIO).required(false).multi(true).build();
    public static final List<PortDef> RTV_INPUTS = List.of(REF_IMAGES_INPUT, REF_VIDEOS_INPUT, REF_AUDIOS_INPUT, PROMPT_INPUT);
    public static final List<PortDef> RTV_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 视频编辑：REFERENCE_IMAGES(multi) + REFERENCE_VIDEOS(multi, 必填) + PROMPT(必填) → VIDEO */
    public static final List<PortDef> VE_EDIT_INPUTS = List.of(
            REF_IMAGES_INPUT, 
            PortDef.builder().name("reference_videos").displayName("参考视频").dataType(PortType.VIDEO).required(true).multi(true).build(),
            PROMPT_REQUIRED);
    public static final List<PortDef> VE_EDIT_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 视频续写/延长：REFERENCE_VIDEOS(multi, 必填, 最多3) + PROMPT(可选) → VIDEO */
    public static final List<PortDef> VE_EXTEND_INPUTS = List.of(
            PortDef.builder().name("reference_videos").displayName("参考视频").dataType(PortType.VIDEO).required(true).multi(true).build(),
            PROMPT_INPUT);
    public static final List<PortDef> VE_EXTEND_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 运镜控制：IMAGE(必填) + PROMPT(可选) → VIDEO */
    public static final List<PortDef> CC_INPUTS = List.of(IMAGE_INPUT, PROMPT_INPUT);
    public static final List<PortDef> CC_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 对口型：VIDEO(必填) + AUDIO(可选) + PROMPT(可选) → VIDEO */
    public static final List<PortDef> LS_INPUTS = List.of(VIDEO_INPUT, AUDIO_INPUT, PROMPT_INPUT);
    public static final List<PortDef> LS_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** 运动控制：VIDEO(必填) + PROMPT(可选) → VIDEO */
    public static final List<PortDef> MC_INPUTS = List.of(VIDEO_INPUT, PROMPT_INPUT);
    public static final List<PortDef> MC_OUTPUTS = List.of(VIDEO_OUTPUT);

    /** Omni Video：PROMPT(可选) + IMAGE(可选) + VIDEO(可选) + AUDIO(可选) → VIDEO */
    public static final List<PortDef> OV_INPUTS = List.of(
            PortDef.builder().name("prompt").displayName("提示词").dataType(PortType.PROMPT).required(false).build(),
            PortDef.builder().name("image").displayName("图片").dataType(PortType.IMAGE).required(false).multi(true).build(),
            PortDef.builder().name("video").displayName("视频").dataType(PortType.VIDEO).required(false).multi(true).build(),
            PortDef.builder().name("audio").displayName("音频").dataType(PortType.AUDIO).required(false).multi(true).build(),
            PortDef.builder().name("element").displayName("主体").dataType(PortType.ELEMENT).required(false).multi(true).build()
    );
    public static final List<PortDef> OV_OUTPUTS = List.of(VIDEO_OUTPUT);

    // ==================== 图片生成类 ====================

    /** 文生图：PROMPT(必填) + IMAGE(可选, multi) + NEGATIVE_PROMPT(可选) → IMAGE */
    public static final List<PortDef> T2I_INPUTS = List.of(
            PROMPT_REQUIRED,
            PortDef.builder().name("image").displayName("参考图").dataType(PortType.IMAGE).required(false).multi(true).build(),
            NEGATIVE_PROMPT_INPUT);
    public static final List<PortDef> T2I_OUTPUTS = List.of(IMAGE_OUTPUT);

    /** Omni Image：PROMPT(可选) + IMAGE(可选) → IMAGE */
    public static final List<PortDef> OI_INPUTS = List.of(
            PortDef.builder().name("prompt").displayName("提示词").dataType(PortType.PROMPT).required(false).build(),
            PortDef.builder().name("image").displayName("参考图").dataType(PortType.IMAGE).required(false).multi(true).build(),
            PortDef.builder().name("element").displayName("主体").dataType(PortType.ELEMENT).required(false).multi(true).build()
    );
    public static final List<PortDef> OI_OUTPUTS = List.of(IMAGE_OUTPUT);

    // ==================== 音频类 ====================

    /** 语音合成：PROMPT(必填) → AUDIO */
    public static final List<PortDef> TTS_INPUTS = List.of(PROMPT_REQUIRED);
    public static final List<PortDef> TTS_OUTPUTS = List.of(AUDIO_OUTPUT);

    /** 音效/BGM生成：PROMPT(必填) → AUDIO */
    public static final List<PortDef> AG_INPUTS = List.of(PROMPT_REQUIRED);
    public static final List<PortDef> AG_OUTPUTS = List.of(AUDIO_OUTPUT);

    // ==================== 特殊节点的端口 ====================

    public static final List<PortDef> IMAGE_INPUT_SLOTS = List.of(); // 无输入
    public static final List<PortDef> IMAGE_INPUT_OUTPUTS = List.of(
            PortDef.builder().name("image").displayName("图片").dataType(PortType.IMAGE).required(false).build()
    );

    public static final List<PortDef> VIDEO_INPUT_SLOTS = List.of();
    public static final List<PortDef> VIDEO_INPUT_OUTPUTS = List.of(
            PortDef.builder().name("video").displayName("视频").dataType(PortType.VIDEO).required(false).build()
    );

    public static final List<PortDef> AUDIO_INPUT_SLOTS = List.of();
    public static final List<PortDef> AUDIO_INPUT_OUTPUTS = List.of(
            PortDef.builder().name("audio").displayName("音频").dataType(PortType.AUDIO).required(false).build()
    );

    public static final List<PortDef> TEXT_INPUT_SLOTS = List.of();
    public static final List<PortDef> TEXT_INPUT_OUTPUTS = List.of(
            PortDef.builder().name("prompt").displayName("提示词").dataType(PortType.PROMPT).required(false).build()
    );

    public static final List<PortDef> IMAGE_PREVIEW_SLOTS = List.of(
            PortDef.builder().name("image").displayName("图片").dataType(PortType.IMAGE).required(true).build()
    );
    public static final List<PortDef> IMAGE_PREVIEW_OUTPUTS = List.of();

    public static final List<PortDef> VIDEO_PREVIEW_SLOTS = List.of(
            PortDef.builder().name("video").displayName("视频").dataType(PortType.VIDEO).required(true).build()
    );
    public static final List<PortDef> VIDEO_PREVIEW_OUTPUTS = List.of();

    // ==================== 主体节点端口 ====================

    /** ElementSource：无输入，输出 ELEMENT 端口 */
    public static final List<PortDef> ELEMENT_SOURCE_SLOTS = List.of();
    public static final List<PortDef> ELEMENT_SOURCE_OUTPUTS = List.of(
            PortDef.builder().name("element").displayName("主体").dataType(PortType.ELEMENT).required(false).build()
    );

    // ==================== 查询方法 ====================

    /**
     * 根据 Capability 获取输入端口定义
     */
    public static List<PortDef> getInputPorts(Capability cap) {
        return CAPABILITY_INPUTS.get(cap);
    }

    /**
     * 根据 Capability 获取输出端口定义
     */
    public static List<PortDef> getOutputPorts(Capability cap) {
        return CAPABILITY_OUTPUTS.get(cap);
    }

    /**
     * 根据特殊节点类型获取输入端口定义
     */
    public static List<PortDef> getSpecialInputPorts(String nodeType) {
        return SPECIAL_INPUTS.get(nodeType);
    }

    /**
     * 根据特殊节点类型获取输出端口定义
     */
    public static List<PortDef> getSpecialOutputPorts(String nodeType) {
        return SPECIAL_OUTPUTS.get(nodeType);
    }

    // ==================== Capability → Ports 映射 ====================

    private static final Map<Capability, List<PortDef>> CAPABILITY_INPUTS = Map.ofEntries(
            Map.entry(Capability.TEXT_TO_VIDEO, T2V_INPUTS),
            Map.entry(Capability.IMAGE_TO_VIDEO, I2V_INPUTS),
            Map.entry(Capability.FIRST_LAST_FRAME, FLF_INPUTS),
            Map.entry(Capability.REFERENCE_TO_VIDEO, RTV_INPUTS),
            Map.entry(Capability.VIDEO_EDIT, VE_EDIT_INPUTS),
            Map.entry(Capability.VIDEO_EXTEND, VE_EXTEND_INPUTS),
            Map.entry(Capability.CAMERA_CONTROL, CC_INPUTS),
            Map.entry(Capability.LIP_SYNC, LS_INPUTS),
            Map.entry(Capability.MOTION_CONTROL, MC_INPUTS),
            Map.entry(Capability.OMNI_VIDEO, OV_INPUTS),
            Map.entry(Capability.TEXT_TO_IMAGE, T2I_INPUTS),
            Map.entry(Capability.OMNI_IMAGE, OI_INPUTS),
            Map.entry(Capability.TEXT_TO_SPEECH, TTS_INPUTS),
            Map.entry(Capability.AUDIO_GENERATE, AG_INPUTS)
    );

    private static final Map<Capability, List<PortDef>> CAPABILITY_OUTPUTS = Map.ofEntries(
            Map.entry(Capability.TEXT_TO_VIDEO, T2V_OUTPUTS),
            Map.entry(Capability.IMAGE_TO_VIDEO, I2V_OUTPUTS),
            Map.entry(Capability.FIRST_LAST_FRAME, FLF_OUTPUTS),
            Map.entry(Capability.REFERENCE_TO_VIDEO, RTV_OUTPUTS),
            Map.entry(Capability.VIDEO_EDIT, VE_EDIT_OUTPUTS),
            Map.entry(Capability.VIDEO_EXTEND, VE_EXTEND_OUTPUTS),
            Map.entry(Capability.CAMERA_CONTROL, CC_OUTPUTS),
            Map.entry(Capability.LIP_SYNC, LS_OUTPUTS),
            Map.entry(Capability.MOTION_CONTROL, MC_OUTPUTS),
            Map.entry(Capability.OMNI_VIDEO, OV_OUTPUTS),
            Map.entry(Capability.TEXT_TO_IMAGE, T2I_OUTPUTS),
            Map.entry(Capability.OMNI_IMAGE, OI_OUTPUTS),
            Map.entry(Capability.TEXT_TO_SPEECH, TTS_OUTPUTS),
            Map.entry(Capability.AUDIO_GENERATE, AG_OUTPUTS)
    );

    // ==================== 特殊节点 → Ports 映射 ====================

    private static final Map<String, List<PortDef>> SPECIAL_INPUTS = Map.of(
            NodeType.IMAGE_INPUT, IMAGE_INPUT_SLOTS,
            NodeType.VIDEO_INPUT, VIDEO_INPUT_SLOTS,
            NodeType.AUDIO_INPUT, AUDIO_INPUT_SLOTS,
            NodeType.TEXT_INPUT, TEXT_INPUT_SLOTS,
            NodeType.IMAGE_PREVIEW, IMAGE_PREVIEW_SLOTS,
            NodeType.VIDEO_PREVIEW, VIDEO_PREVIEW_SLOTS,
            NodeType.ELEMENT_SOURCE, ELEMENT_SOURCE_SLOTS
    );

    private static final Map<String, List<PortDef>> SPECIAL_OUTPUTS = Map.of(
            NodeType.IMAGE_INPUT, IMAGE_INPUT_OUTPUTS,
            NodeType.VIDEO_INPUT, VIDEO_INPUT_OUTPUTS,
            NodeType.AUDIO_INPUT, AUDIO_INPUT_OUTPUTS,
            NodeType.TEXT_INPUT, TEXT_INPUT_OUTPUTS,
            NodeType.IMAGE_PREVIEW, IMAGE_PREVIEW_OUTPUTS,
            NodeType.VIDEO_PREVIEW, VIDEO_PREVIEW_OUTPUTS,
            NodeType.ELEMENT_SOURCE, ELEMENT_SOURCE_OUTPUTS
    );
}