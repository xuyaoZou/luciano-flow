package com.luciano.adapter.kling;

import com.luciano.adapter.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.luciano.adapter.kling.KlingConstants.*;

/**
 * Kling 适配器 Schema 定义
 * 为每个 Capability 提供完整的参数 Schema。
 * 对应 ComfyUI 各 Node 的 INPUT_TYPES 定义。
 */
@Component
@Slf4j
public class KlingSchemaProvider {

    // ==================== TEXT_TO_VIDEO ====================

    public CapabilitySchema textToVideoSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.TEXT_TO_VIDEO)
                .displayName("文生视频")
                .description("通过文字描述生成视频")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").description("描述视频内容的文字")
                                .max(MAX_PROMPT_T2V).multilingual(true)
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("negative_prompt").type(ParamType.STRING)
                                .displayName("负向提示词").advanced(true)
                                .group("基础").build(),
                        ParamDef.builder()
                                .name("mode").type(ParamType.ENUM)
                                .displayName("生成模式").description("standard=标准, pro=专业")
                                .options(List.of(MODE_STD, MODE_PRO))
                                .defaultValue(MODE_STD)
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("model_name").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(
                                        MODEL_KLING_V1_6, MODEL_KLING_V2_MASTER,
                                        MODEL_KLING_V2_1_MASTER, MODEL_KLING_V2_5_TURBO))
                                .defaultValue(MODEL_KLING_V1_6)
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.ENUM)
                                .displayName("时长").options(DURATIONS)
                                .defaultValue("5")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例").options(ASPECT_RATIOS)
                                .defaultValue("16:9")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("cfg_scale").type(ParamType.FLOAT)
                                .displayName("CFG Scale").description("提示词跟随强度")
                                .min(0.0).max(1.0).defaultValue(0.5)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("camera_control").type(ParamType.STRING)
                                .displayName("运镜控制").description("JSON 格式的运镜参数")
                                .condition("mode=std")
                                .group("运镜").advanced(true).build()
                ))
                .constraints(List.of(
                        ParamConstraint.builder()
                                .type(ParamConstraint.ConstraintType.REQUIRES)
                                .params(List.of("model_name", "mode"))
                                .message("kling-v2-master 仅支持 pro 模式")
                                .conditionParam("model_name")
                                .conditionValue(MODEL_KLING_V2_MASTER)
                                .build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(AVG_DURATION_T2V)
                .costHint("约 $0.28~2.8/次（取决于模式和时长）")
                .build();
    }

    // ==================== IMAGE_TO_VIDEO ====================

    public CapabilitySchema imageToVideoSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.IMAGE_TO_VIDEO)
                .displayName("图生视频")
                .description("通过图片 + 文字描述生成视频")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").max(MAX_PROMPT_I2V)
                                .multilingual(true).group("基础").build(),
                        ParamDef.builder()
                                .name("image").type(ParamType.IMAGE)
                                .displayName("起始帧图片").group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("negative_prompt").type(ParamType.STRING)
                                .displayName("负向提示词").advanced(true).build(),
                        ParamDef.builder()
                                .name("mode").type(ParamType.ENUM)
                                .displayName("生成模式")
                                .options(List.of(MODE_STD, MODE_PRO))
                                .defaultValue(MODE_STD).group("模型").build(),
                        ParamDef.builder()
                                .name("model_name").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(
                                        MODEL_KLING_V1_6, MODEL_KLING_V2_MASTER,
                                        MODEL_KLING_V2_1_MASTER, MODEL_KLING_V2_5_TURBO))
                                .defaultValue(MODEL_KLING_V1_6).group("模型").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.ENUM)
                                .displayName("时长").options(DURATIONS)
                                .defaultValue("5").group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例").options(ASPECT_RATIOS)
                                .defaultValue("16:9").group("视频").build(),
                        ParamDef.builder()
                                .name("cfg_scale").type(ParamType.FLOAT)
                                .displayName("CFG Scale")
                                .min(0.0).max(1.0).defaultValue(0.5)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0).group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("camera_control").type(ParamType.STRING)
                                .displayName("运镜控制").group("运镜").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(AVG_DURATION_I2V)
                .costHint("约 $0.35~0.98/次")
                .build();
    }

    // ==================== FIRST_LAST_FRAME ====================

    public CapabilitySchema firstLastFrameSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.FIRST_LAST_FRAME)
                .displayName("首尾帧生成视频")
                .description("通过起始帧和可选的结束帧生成视频")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").max(MAX_PROMPT_I2V)
                                .multilingual(true).group("基础").build(),
                        ParamDef.builder()
                                .name("first_frame").type(ParamType.IMAGE)
                                .displayName("首帧图片").group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("last_frame").type(ParamType.IMAGE)
                                .displayName("尾帧图片").group("基础").build(),
                        ParamDef.builder()
                                .name("mode").type(ParamType.ENUM)
                                .displayName("生成模式")
                                .options(List.of(MODE_PRO))
                                .defaultValue(MODE_PRO).group("模型").build(),
                        ParamDef.builder()
                                .name("model_name").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(
                                        MODEL_KLING_V1_5, MODEL_KLING_V1_6,
                                        MODEL_KLING_V2_1, MODEL_KLING_V2_5_TURBO))
                                .defaultValue(MODEL_KLING_V1_6).group("模型").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.ENUM)
                                .displayName("时长").options(DURATIONS)
                                .defaultValue("5").group("视频").build(),
                        ParamDef.builder()
                                .name("cfg_scale").type(ParamType.FLOAT)
                                .displayName("CFG Scale")
                                .min(0.0).max(1.0).defaultValue(0.5)
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(AVG_DURATION_FLF)
                .costHint("约 $0.49~0.98/次（首尾帧仅支持 pro 模式）")
                .build();
    }

    // ==================== TEXT_TO_IMAGE ====================

    public CapabilitySchema textToImageSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.TEXT_TO_IMAGE)
                .displayName("文生图")
                .description("通过文字描述生成图片")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").max(MAX_PROMPT_IMAGE)
                                .multilingual(true).group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("negative_prompt").type(ParamType.STRING)
                                .displayName("负向提示词")
                                .max(MAX_NEGATIVE_PROMPT_IMAGE).advanced(true).build(),
                        ParamDef.builder()
                                .name("model_name").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_KLING_V3_OMNI, MODEL_KLING_IMAGE_O1))
                                .defaultValue(MODEL_KLING_V3_OMNI).group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(IMAGE_RESOLUTIONS)
                                .defaultValue("1K").group("图片").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(IMAGE_ASPECT_RATIOS)
                                .defaultValue("16:9").group("图片").build(),
                        ParamDef.builder()
                                .name("series_amount").type(ParamType.ENUM)
                                .displayName("系列生成数量")
                                .options(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"))
                                .defaultValue("1").group("图片").build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0).group("高级").advanced(true).build()
                ))
                .constraints(List.of(
                        ParamConstraint.builder()
                                .type(ParamConstraint.ConstraintType.MUTEX)
                                .params(List.of("series_amount", "model_name"))
                                .message("kling-image-o1 不支持系列生成")
                                .conditionParam("model_name")
                                .conditionValue(MODEL_KLING_IMAGE_O1)
                                .build()
                ))
                .outputFormats(List.of("png", "jpg"))
                .estimatedDurationMs(AVG_DURATION_IMAGE)
                .costHint("约 $0.028~0.056/张")
                .build();
    }

    // ==================== LIP_SYNC ====================

    public CapabilitySchema lipSyncSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.LIP_SYNC)
                .displayName("对口型")
                .description("上传视频和音频/文本，生成口型同步的视频。" +
                        "视频必须包含清晰人脸，否则返回错误。" +
                        "支持两种模式：text2video（文本驱动）和 audio2video（音频驱动）")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("video").type(ParamType.VIDEO)
                                .displayName("输入视频").description("包含人脸的视频，2-10秒，≤100MB")
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("mode").type(ParamType.ENUM)
                                .displayName("驱动模式").description("text2video=文本驱动，audio2video=音频驱动")
                                .options(List.of("text2video", "audio2video"))
                                .defaultValue("text2video")
                                .group("模式").build(),
                        ParamDef.builder()
                                .name("text").type(ParamType.STRING)
                                .displayName("文本内容").description("text2video 模式必填，最长120字符")
                                .max(MAX_PROMPT_LIP_SYNC)
                                .group("文本驱动").build(),
                        ParamDef.builder()
                                .name("voice_id").type(ParamType.ENUM)
                                .displayName("音色").description("text2video 模式必填")
                                .options(List.of(
                                        "ai_shatang", "girlfriend_4_speech02", "genshin_vindi2", "zhinen_xuesheng",
                                        "AOT", "genshin_klee2", "genshin_kirara", "ai_kaiya",
                                        "oversea_male1", "ai_chenjiahao_712", "chat1_female_new-3",
                                        "chat_0407_5-1", "cartoon-boy-07", "cartoon-girl-01",
                                        "ai_huangzhong_712", "ai_huangyaoshi_712", "ai_laoguowang_712",
                                        "chengshu_jiejie", "you_pingjing", "laopopo_speech02"))
                                .group("文本驱动").build(),
                        ParamDef.builder()
                                .name("voice_language").type(ParamType.ENUM)
                                .displayName("语言").options(LIP_SYNC_LANGUAGES)
                                .defaultValue("zh").group("文本驱动").build(),
                        ParamDef.builder()
                                .name("voice_speed").type(ParamType.FLOAT)
                                .displayName("语速").min(0.8).max(2.0)
                                .defaultValue(1.0).group("文本驱动").build(),
                        ParamDef.builder()
                                .name("audio_url").type(ParamType.STRING)
                                .displayName("音频URL").description("audio2video 模式必填，支持 .mp3/.wav/.m4a/.aac，≤5MB")
                                .group("音频驱动").build()
                ))
                .constraints(List.of(
                        ParamConstraint.builder()
                                .type(ParamConstraint.ConstraintType.MUTEX)
                                .params(List.of("text", "audio_url"))
                                .message("文本和音频不能同时使用，请选择其一")
                                .build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(AVG_DURATION_LIP_SYNC)
                .costHint("约 $0.49/次")
                .build();
    }

    // ==================== CAMERA_CONTROL ====================

    public CapabilitySchema cameraControlSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.CAMERA_CONTROL)
                .displayName("运镜控制")
                .description("控制摄像机运动（平移、倾斜、旋转、缩放）")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").max(MAX_PROMPT_T2V)
                                .multilingual(true).group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("camera_type").type(ParamType.ENUM)
                                .displayName("运镜类型")
                                .description("选择运镜方式：简单运镜可指定轴向和强度；预设运镜一键生成效果")
                                .options(List.of(CAMERA_TYPE_SIMPLE, CAMERA_TYPE_FORWARD_UP,
                                        CAMERA_TYPE_DOWN_BACK, CAMERA_TYPE_RIGHT_TURN_FORWARD,
                                        CAMERA_TYPE_LEFT_TURN_FORWARD))
                                .defaultValue(CAMERA_TYPE_SIMPLE).group("运镜").advanced(false).build(),
                        ParamDef.builder()
                                .name("camera_axis").type(ParamType.ENUM)
                                .displayName("运镜轴向")
                                .description("simple 类型：选择一个运镜方向")
                                .options(List.of("horizontal", "vertical", "pan", "tilt", "roll", "zoom"))
                                .defaultValue("zoom").group("运镜").advanced(false)
                                .showWhenField("camera_type").showWhenValue(CAMERA_TYPE_SIMPLE).build(),
                        ParamDef.builder()
                                .name("camera_value").type(ParamType.FLOAT)
                                .displayName("运镜强度")
                                .description("simple 类型的运镜值，范围 -10~10")
                                .min(-10.0).max(10.0)
                                .defaultValue(5.0).step(0.25).group("运镜").advanced(false)
                                .showWhenField("camera_type").showWhenValue(CAMERA_TYPE_SIMPLE).build(),
                        ParamDef.builder()
                                .name("horizontal").type(ParamType.FLOAT)
                                .displayName("水平移动").min(-10.0).max(10.0)
                                .defaultValue(0.0).step(0.25).group("运镜").advanced(true).build(),
                        ParamDef.builder()
                                .name("vertical").type(ParamType.FLOAT)
                                .displayName("垂直移动").min(-10.0).max(10.0)
                                .defaultValue(0.0).step(0.25).group("运镜").advanced(true).build(),
                        ParamDef.builder()
                                .name("pan").type(ParamType.FLOAT)
                                .displayName("水平旋转").min(-10.0).max(10.0)
                                .defaultValue(0.0).step(0.25).group("运镜").advanced(true).build(),
                        ParamDef.builder()
                                .name("tilt").type(ParamType.FLOAT)
                                .displayName("垂直旋转").min(-10.0).max(10.0)
                                .defaultValue(0.0).step(0.25).group("运镜").advanced(true).build(),
                        ParamDef.builder()
                                .name("roll").type(ParamType.FLOAT)
                                .displayName("翻滚").min(-10.0).max(10.0)
                                .defaultValue(0.0).step(0.25).group("运镜").advanced(true).build(),
                        ParamDef.builder()
                                .name("zoom").type(ParamType.FLOAT)
                                .displayName("缩放").min(-10.0).max(10.0)
                                .defaultValue(0.0).step(0.25).group("运镜").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(AVG_DURATION_T2V)
                .costHint("约 $0.14/次")
                .build();
    }

    // ==================== VIDEO_EXTEND ====================

    public CapabilitySchema videoExtendSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.VIDEO_EXTEND)
                .displayName("视频续写")
                .description("延长已有视频的时长。" +
                        "仅支持 pro 模式生成的视频续写，std 模式视频不可续写。" +
                        "video_id 为可灵生成的视频ID（非URL）")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("video_id").type(ParamType.STRING)
                                .displayName("视频ID").description("可灵生成的视频ID（非URL），仅支持pro模式生成的视频")
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("续写提示词").max(MAX_PROMPT_I2V)
                                .multilingual(true).group("基础").build(),
                        ParamDef.builder()
                                .name("model_name").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_KLING_V1_6, MODEL_KLING_V2_MASTER, MODEL_KLING_V2_1_MASTER))
                                .defaultValue(MODEL_KLING_V1_6).group("模型").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.ENUM)
                                .displayName("续写时长").options(DURATIONS)
                                .defaultValue("5").group("视频").build(),
                        ParamDef.builder()
                                .name("mode").type(ParamType.ENUM)
                                .displayName("生成模式").options(List.of(MODE_STD, MODE_PRO))
                                .defaultValue(MODE_STD).group("模型").build(),
                        ParamDef.builder()
                                .name("negative_prompt").type(ParamType.STRING)
                                .displayName("反向提示词").advanced(true)
                                .group("高级").build(),
                        ParamDef.builder()
                                .name("cfg_scale").type(ParamType.FLOAT)
                                .displayName("创意度").min(0.0).max(1.0)
                                .defaultValue(0.5).advanced(true)
                                .group("高级").build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(AVG_DURATION_EXTEND)
                .costHint("约 $0.14~0.98/次")
                .build();
    }

    // ==================== OMNI_VIDEO ====================

    public CapabilitySchema omniVideoSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.OMNI_VIDEO)
                .displayName("视频Omni（多模态）")
                .description("多模态视频生成，支持多图参考、主体参考、视频编辑、多镜头分镜、首尾帧、4K、配音。" +
                        "支持模型：kling-video-o1、kling-v3-omni。" +
                        "注意：走独立端点 /v1/videos/omni-video")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").max(MAX_PROMPT_T2V)
                                .multilingual(true).group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model_name").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_KLING_VIDEO_O1, MODEL_KLING_V3_OMNI))
                                .defaultValue(MODEL_KLING_VIDEO_O1).group("模型").build(),
                        ParamDef.builder()
                                .name("mode").type(ParamType.ENUM)
                                .displayName("生成模式").description("std=标准, pro=专业, 4k=超清")
                                .options(OMNI_VIDEO_MODES)
                                .defaultValue(MODE_STD).group("模型").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.ENUM)
                                .displayName("时长").description("3-15秒")
                                .options(OMNI_VIDEO_DURATIONS)
                                .defaultValue("5").group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例").options(ASPECT_RATIOS)
                                .defaultValue("16:9").group("视频").build(),
                        // 参考图（含首尾帧）
                        ParamDef.builder()
                                .name("image_list").type(ParamType.STRING)
                                .displayName("参考图列表").description("JSON数组，每项含 type(first_frame/end_frame) 和 url")
                                .advanced(true).group("参考图").build(),
                        // 主体参考
                        ParamDef.builder()
                                .name("element_list").type(ParamType.ELEMENT_LIST)
                                .displayName("主体参考").description("通过主体节点连线传入，或手动输入 element_id")
                                .advanced(true).group("参考图").build(),
                        // 参考视频
                        ParamDef.builder()
                                .name("video_list").type(ParamType.STRING)
                                .displayName("参考视频列表").description("JSON数组，每项含 video_url 和 refer_type(feature/base)")
                                .advanced(true).group("参考视频").build(),
                        // 多镜头
                        ParamDef.builder()
                                .name("multi_shot").type(ParamType.STRING)
                                .displayName("多镜头开关").description("true=开启多镜头")
                                .advanced(true).group("多镜头").build(),
                        ParamDef.builder()
                                .name("shot_type").type(ParamType.ENUM)
                                .displayName("分镜类型")
                                .options(List.of(SHOT_TYPE_CUSTOMIZE, SHOT_TYPE_INTELLIGENCE))
                                .advanced(true).group("多镜头").build(),
                        ParamDef.builder()
                                .name("multi_prompt").type(ParamType.STRING)
                                .displayName("分镜提示词").description("JSON数组，各分镜的提示词")
                                .advanced(true).group("多镜头").build(),
                        // 配音
                        ParamDef.builder()
                                .name("sound").type(ParamType.ENUM)
                                .displayName("配音").options(List.of(SOUND_ON, SOUND_OFF))
                                .advanced(true).group("音频").build(),
                        ParamDef.builder()
                                .name("keep_original_sound").type(ParamType.STRING)
                                .displayName("保留原声").description("true/false")
                                .advanced(true).group("音频").build(),
                        // 通用参数
                        ParamDef.builder()
                                .name("cfg_scale").type(ParamType.FLOAT)
                                .displayName("CFG Scale").min(0.0).max(1.0)
                                .defaultValue(0.5).advanced(true).group("高级").build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0).advanced(true).group("高级").build(),
                        ParamDef.builder()
                                .name("negative_prompt").type(ParamType.STRING)
                                .displayName("负向提示词").advanced(true).group("高级").build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(AVG_DURATION_OMNI)
                .costHint("约 $0.42~6.30/次（std=$0.084/秒, pro=$0.112/秒, 4k=$0.42/秒）")
                .build();
    }

    // ==================== OMNI_IMAGE ====================

    public CapabilitySchema omniImageSchema() {
        return CapabilitySchema.builder()
                .adapterId("kling")
                .capability(Capability.OMNI_IMAGE)
                .displayName("图像Omni（多模态）")
                .description("多模态图像生成，支持多图参考、主体参考、组图模式。" +
                        "支持模型：kling-image-o1、kling-v3-omni。" +
                        "注意：走独立端点 /v1/images/omni-image")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").max(MAX_PROMPT_IMAGE)
                                .multilingual(true).group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model_name").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_KLING_IMAGE_O1, MODEL_KLING_V3_OMNI))
                                .defaultValue(MODEL_KLING_IMAGE_O1).group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(OMNI_IMAGE_RESOLUTIONS)
                                .defaultValue("1k").group("图片").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(ASPECT_RATIOS)
                                .defaultValue("16:9").group("图片").build(),
                        ParamDef.builder()
                                .name("n").type(ParamType.INTEGER)
                                .displayName("生成数量").min(1).max(9)
                                .defaultValue(1).group("图片").build(),
                        // 组图模式
                        ParamDef.builder()
                                .name("result_type").type(ParamType.ENUM)
                                .displayName("结果类型")
                                .options(List.of(RESULT_TYPE_SINGLE, RESULT_TYPE_SERIES))
                                .advanced(true).group("组图").build(),
                        ParamDef.builder()
                                .name("series_amount").type(ParamType.STRING)
                                .displayName("组图数量").description("2-9 或 auto")
                                .advanced(true).group("组图").build(),
                        // 参考图
                        ParamDef.builder()
                                .name("image_list").type(ParamType.STRING)
                                .displayName("参考图列表").description("JSON数组")
                                .advanced(true).group("参考图").build(),
                        // 主体参考
                        ParamDef.builder()
                                .name("element_list").type(ParamType.ELEMENT_LIST)
                                .displayName("主体参考").description("通过主体节点连线传入，或手动输入 element_id")
                                .advanced(true).group("参考图").build(),
                        // 通用参数
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0).advanced(true).group("高级").build()
                ))
                .outputFormats(List.of("png", "jpg"))
                .estimatedDurationMs(AVG_DURATION_IMAGE)
                .costHint("约 $0.028~0.056/张")
                .build();
    }
}
