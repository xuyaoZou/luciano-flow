package com.luciano.adapter.seedance;

import com.luciano.adapter.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.luciano.adapter.seedance.SeedanceConstants.*;

/**
 * Seedance 适配器 Schema 定义
 * 为每个 Capability 提供完整的参数 Schema。
 * <p>
 * 关键差异（与 Kling 对比，验证架构通用性）：
 * 1. Seedance 2.0 支持多参考图(9张)/视频(3个)/音频(3个) — Kling 无此能力
 * 2. Seedance 按 token 计费 — Kling 按次计费
 * 3. Seedance 1.x 参数拼在 prompt 里 — Kling 参数在 JSON body
 * 4. Seedance 有"自适应画面比" — Kling 无
 * 5. Seedance 有 camera_fixed 布尔开关 — Kling 有6轴运镜控制
 * 6. Seedance 有 generate_audio 开关 — Kling 无
 */
@Component
@Slf4j
public class SeedanceSchemaProvider {

    // ==================== TEXT_TO_VIDEO (1.x) ====================

    public CapabilitySchema textToVideoV1Schema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.TEXT_TO_VIDEO)
                .displayName("文生视频 (Seedance 1.x)")
                .description("通过文字描述生成视频，参数拼在 prompt 中")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").description("视频内容描述，注意不要包含 --resolution 等保留参数")
                                .multilingual(true)
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(
                                        MODEL_SD15_PRO, MODEL_SD10_PRO,
                                        MODEL_SD10_PRO_FAST, MODEL_SD10_LITE_T2V))
                                .defaultValue(MODEL_SD10_PRO_FAST)
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(RESOLUTIONS_V1)
                                .defaultValue("720p")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(ASPECT_RATIOS_V1)
                                .defaultValue("16:9")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.INTEGER)
                                .displayName("时长(秒)")
                                .min(DURATION_MIN_V1).max(DURATION_MAX_V1)
                                .defaultValue(5).step(1)
                                .description("与 frames 二选一，frames 优先。-1=智能时长（仅 1.5 Pro 支持），有效范围: 3~12秒")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("frames").type(ParamType.INTEGER)
                                .displayName("帧数")
                                .min(29).max(289).step(4)
                                .description("25+4n 格式（29~289），优先于 duration")
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("camera_fixed").type(ParamType.BOOLEAN)
                                .displayName("固定镜头").description("固定摄像机不移动（不保证效果）")
                                .defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("generate_audio").type(ParamType.BOOLEAN)
                                .displayName("生成音频").description("仅 Seedance 1.5 Pro 支持")
                                .defaultValue(false)
                                .group("音频").advanced(true).build(),
                        ParamDef.builder()
                                .name("draft").type(ParamType.BOOLEAN)
                                .displayName("样片模式").description("仅 1.5 Pro 支持，480p 预览视频，消耗 token 更少")
                                .defaultValue(false)
                                .group("高级").advanced(true).build()
                ))
                .constraints(List.of(
                        ParamConstraint.builder()
                                .type(ParamConstraint.ConstraintType.REQUIRES)
                                .params(List.of("generate_audio"))
                                .message("generate_audio 仅 seedance-1-5-pro 支持")
                                .conditionParam("model")
                                .conditionValue(MODEL_SD15_PRO)
                                .negate(true)
                                .build(),
                        ParamConstraint.builder()
                                .type(ParamConstraint.ConstraintType.REQUIRES)
                                .params(List.of("draft"))
                                .message("draft 仅 seedance-1-5-pro 支持")
                                .conditionParam("model")
                                .conditionValue(MODEL_SD15_PRO)
                                .negate(true)
                                .build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(200000)
                .costHint("约 $0.09~1.18/次（取决于模型和分辨率）")
                .build();
    }

    // ==================== TEXT_TO_VIDEO (2.0) ====================

    public CapabilitySchema textToVideoV2Schema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.TEXT_TO_VIDEO)
                .displayName("文生视频 (Seedance 2.0)")
                .description("Seedance 2.0 使用 JSON body 传参，支持音频生成")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").multilingual(true)
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(
                                        MODEL_SD20, MODEL_SD20_FAST,
                                        MODEL_SD15_PRO, MODEL_SD10_PRO,
                                        MODEL_SD10_PRO_FAST, MODEL_SD10_LITE_T2V))
                                .defaultValue(MODEL_SD20)
                                .description("2.0 最高质量，2.0 Fast 速度优先；1.x 系列选后自动切换参数")
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(RESOLUTIONS_SD20)
                                .defaultValue("720p")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(ASPECT_RATIOS_V2)
                                .defaultValue("16:9")
                                .description("adaptive: 根据首帧自动匹配")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.INTEGER)
                                .displayName("时长(秒)")
                                .min(DURATION_MIN_V2).max(DURATION_MAX_V2)
                                .defaultValue(DURATION_DEFAULT_V2).step(1)
                                .description("-1=智能时长（模型自动决定），有效范围: 4~15秒")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("generate_audio").type(ParamType.BOOLEAN)
                                .displayName("生成音频").defaultValue(true)
                                .group("音频").build(),
                        ParamDef.builder()
                                .name("return_last_frame").type(ParamType.BOOLEAN)
                                .displayName("返回尾帧").description("生成视频时同时返回尾帧图，可用于续编")
                                .defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("camera_fixed").type(ParamType.BOOLEAN)
                                .displayName("固定镜头").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("web_search").type(ParamType.BOOLEAN)
                                .displayName("联网搜索").description("生成前先搜索互联网获取参考资料")
                                .defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("service_tier").type(ParamType.ENUM)
                                .displayName("服务层级").description("flex=离线推理半价，默认按需")
                                .options(List.of("flex"))
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(300000)
                .costHint("按 token 计费，约 $0.07~0.73/次")
                .build();
    }

    // ==================== IMAGE_TO_VIDEO ====================

    public CapabilitySchema imageToVideoSchema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.IMAGE_TO_VIDEO)
                .displayName("图生视频")
                .description("通过图片+文字描述生成视频")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").multilingual(true)
                                .group("基础").build(),
                        ParamDef.builder()
                                .name("image").type(ParamType.IMAGE)
                                .displayName("图片")
                                .description("首张图作为首帧，其余作为参考图（Seedance 2.0 支持多图参考）")
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(
                                        MODEL_SD20, MODEL_SD20_FAST,
                                        MODEL_SD15_PRO, MODEL_SD10_PRO,
                                        MODEL_SD10_PRO_FAST, MODEL_SD10_LITE_I2V))
                                .defaultValue(MODEL_SD20)
                                .description("2.0 支持多图参考+音频生成，1.x 仅支持单张首帧")
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(RESOLUTIONS_SD20)
                                .defaultValue("720p")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(ASPECT_RATIOS_V2)
                                .defaultValue("adaptive")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.INTEGER)
                                .displayName("时长(秒)")
                                .min(DURATION_MIN_V1).max(DURATION_MAX_V2)
                                .defaultValue(5).step(1)
                                .description("-1=智能时长，2.0: 4~15秒，1.x: 3~12秒")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("camera_fixed").type(ParamType.BOOLEAN)
                                .displayName("固定镜头").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("generate_audio").type(ParamType.BOOLEAN)
                                .displayName("生成音频")
                                .defaultValue(false)
                                .group("音频").advanced(true).build(),
                        ParamDef.builder()
                                .name("return_last_frame").type(ParamType.BOOLEAN)
                                .displayName("返回尾帧").description("生成视频时同时返回尾帧图，可用于续编")
                                .defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("draft").type(ParamType.BOOLEAN)
                                .displayName("样片模式").description("仅 1.5 Pro 支持，480p 预览视频")
                                .defaultValue(false)
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(200000)
                .costHint("约 $0.09~1.18/次")
                .build();
    }

    // ==================== FIRST_LAST_FRAME ====================

    public CapabilitySchema firstLastFrameSchema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.FIRST_LAST_FRAME)
                .displayName("首尾帧生成视频")
                .description("通过首帧和尾帧图片生成视频（支持 1.x 和 2.0）")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").multilingual(true)
                                .group("基础").build(),
                        ParamDef.builder()
                                .name("first_frame").type(ParamType.IMAGE)
                                .displayName("首帧图片")
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("last_frame").type(ParamType.IMAGE)
                                .displayName("尾帧图片")
                                .group("基础").build(),
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(
                                        MODEL_SD15_PRO, MODEL_SD10_PRO,
                                        MODEL_SD10_LITE_I2V, MODEL_SD20, MODEL_SD20_FAST))
                                .defaultValue(MODEL_SD20)
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(RESOLUTIONS_SD20)
                                .defaultValue("720p")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(ASPECT_RATIOS_V2)
                                .defaultValue("adaptive")
                                .description("2.0 建议用 adaptive 避免拉伸")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.INTEGER)
                                .displayName("时长(秒)")
                                .min(DURATION_MIN_V1).max(DURATION_MAX_V2)
                                .defaultValue(DURATION_DEFAULT_V2).step(1)
                                .description("-1=智能时长，2.0: 4~15秒，1.x: 3~12秒")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("generate_audio").type(ParamType.BOOLEAN)
                                .displayName("生成音频").defaultValue(true)
                                .group("音频").build(),
                        ParamDef.builder()
                                .name("camera_fixed").type(ParamType.BOOLEAN)
                                .displayName("固定镜头").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("draft").type(ParamType.BOOLEAN)
                                .displayName("样片模式").description("仅 1.5 Pro 支持，480p 预览视频")
                                .defaultValue(false)
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(300000)
                .costHint("约 $0.07~0.73/次")
                .build();
    }

    // ==================== REFERENCE_TO_VIDEO (2.0 独有) ====================

    public CapabilitySchema referenceToVideoSchema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.REFERENCE_TO_VIDEO)
                .displayName("参考图/视频/音频生成视频")
                .description("Seedance 2.0 独有：支持多参考图(9)、参考视频(3)、参考音频(3) 生成视频")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").multilingual(true)
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_SD20, MODEL_SD20_FAST))
                                .defaultValue(MODEL_SD20)
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(RESOLUTIONS_SD20)
                                .defaultValue("720p")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(ASPECT_RATIOS_V2)
                                .defaultValue("adaptive")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.INTEGER)
                                .displayName("时长(秒)")
                                .min(DURATION_MIN_V2).max(DURATION_MAX_V2)
                                .defaultValue(DURATION_DEFAULT_V2).step(1)
                                .description("-1=智能时长，有效范围: 4~15秒")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("generate_audio").type(ParamType.BOOLEAN)
                                .displayName("生成音频").defaultValue(true)
                                .group("音频").build(),
                        ParamDef.builder()
                                .name("reference_images").type(ParamType.IMAGE_LIST)
                                .displayName("参考图片").description("最多 9 张")
                                .max(9)
                                .group("参考").build(),
                        ParamDef.builder()
                                .name("reference_videos").type(ParamType.VIDEO_LIST)
                                .displayName("参考视频").description("最多 3 个，总时长 ≤15秒")
                                .max(3)
                                .group("参考").build(),
                        ParamDef.builder()
                                .name("reference_audios").type(ParamType.AUDIO_LIST)
                                .displayName("参考音频").description("最多 3 个，总时长 ≤15秒")
                                .max(3)
                                .group("参考").build(),
                        ParamDef.builder()
                                .name("camera_fixed").type(ParamType.BOOLEAN)
                                .displayName("固定镜头").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(400000)
                .costHint("按 token 计费，有参考视频时费用更低")
                .build();
    }

    // ==================== VIDEO_EDIT (Seedance 2.0 独有) ====================

    public CapabilitySchema videoEditSchema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.VIDEO_EDIT)
                .displayName("视频编辑")
                .description("Seedance 2.0 独有：通过参考图+参考视频进行局部编辑/替换")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").multilingual(true)
                                .description("描述编辑意图，如“将视频1中的香水替换成图片1中的面霜”")
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_SD20, MODEL_SD20_FAST))
                                .defaultValue(MODEL_SD20)
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("resolution").type(ParamType.ENUM)
                                .displayName("分辨率")
                                .options(RESOLUTIONS_SD20)
                                .defaultValue("720p")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("aspect_ratio").type(ParamType.ENUM)
                                .displayName("画面比例")
                                .options(ASPECT_RATIOS_V2)
                                .defaultValue("adaptive")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.INTEGER)
                                .displayName("时长(秒)")
                                .min(DURATION_MIN_V2).max(DURATION_MAX_V2)
                                .defaultValue(DURATION_DEFAULT_V2).step(1)
                                .description("-1=智能时长，有效范围: 4~15秒")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("generate_audio").type(ParamType.BOOLEAN)
                                .displayName("生成音频").defaultValue(true)
                                .group("音频").build(),
                        ParamDef.builder()
                                .name("reference_images").type(ParamType.IMAGE_LIST)
                                .displayName("参考图片").description("用于替换/编辑的素材图")
                                .max(9)
                                .group("参考").build(),
                        ParamDef.builder()
                                .name("reference_videos").type(ParamType.VIDEO_LIST)
                                .displayName("参考视频").description("待编辑的原始视频")
                                .max(3)
                                .required(true)
                                .group("参考").build(),
                        ParamDef.builder()
                                .name("camera_fixed").type(ParamType.BOOLEAN)
                                .displayName("固定镜头").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(400000)
                .costHint("按 token 计费")
                .build();
    }

    // ==================== VIDEO_EXTEND (Seedance 2.0 独有) ====================

    public CapabilitySchema videoExtendSchema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.VIDEO_EXTEND)
                .displayName("延长视频")
                .description("Seedance 2.0 独有：最多 3 段视频片段串联延长")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").multilingual(true)
                                .description("描述视频延长的内容")
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_SD20, MODEL_SD20_FAST))
                                .defaultValue(MODEL_SD20)
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("duration").type(ParamType.INTEGER)
                                .displayName("时长(秒)")
                                .min(DURATION_MIN_V2).max(DURATION_MAX_V2)
                                .defaultValue(DURATION_DEFAULT_V2).step(1)
                                .description("-1=智能时长，有效范围: 4~15秒")
                                .group("视频").build(),
                        ParamDef.builder()
                                .name("generate_audio").type(ParamType.BOOLEAN)
                                .displayName("生成音频").defaultValue(true)
                                .group("音频").build(),
                        ParamDef.builder()
                                .name("reference_videos").type(ParamType.VIDEO_LIST)
                                .displayName("参考视频").description("待延长的视频片段，最多 3 个")
                                .max(3)
                                .required(true)
                                .group("参考").build(),
                        ParamDef.builder()
                                .name("camera_fixed").type(ParamType.BOOLEAN)
                                .displayName("固定镜头").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("mp4"))
                .estimatedDurationMs(400000)
                .costHint("按 token 计费")
                .build();
    }

    // ==================== TEXT_TO_IMAGE (Seedream) ====================

    public CapabilitySchema textToImageSchema() {
        return CapabilitySchema.builder()
                .adapterId("seedance")
                .capability(Capability.TEXT_TO_IMAGE)
                .displayName("文生图 (Seedream)")
                .description("字节 Seedream 图片生成，支持 4.0/4.5/5.0 Lite，最高 4K")
                .requiredParams(List.of(
                        ParamDef.builder()
                                .name("prompt").type(ParamType.STRING)
                                .displayName("提示词").multilingual(true)
                                .group("基础").build()
                ))
                .optionalParams(List.of(
                        ParamDef.builder()
                                .name("model").type(ParamType.ENUM)
                                .displayName("模型版本")
                                .options(List.of(MODEL_SEEDREAM_5_LITE, MODEL_SEEDREAM_45, MODEL_SEEDREAM_40))
                                .defaultValue(MODEL_SEEDREAM_5_LITE)
                                .description("5.0 Lite 最新，4.5 质量好，4.0 经济")
                                .group("模型").build(),
                        ParamDef.builder()
                                .name("size_preset").type(ParamType.ENUM)
                                .displayName("尺寸预设")
                                .options(List.of(
                                        "1024×1024", "1280×720", "720×1280",
                                        "1536×1024", "1024×1536",
                                        "2048×2048", "2560×1440", "1440×2560",
                                        "Custom"))
                                .defaultValue("2048×2048")
                                .group("图片").build(),
                        ParamDef.builder()
                                .name("width").type(ParamType.INTEGER)
                                .displayName("自定义宽度").min(1024).max(6240).step(2)
                                .defaultValue(2048)
                                .condition("size_preset=Custom")
                                .group("图片").advanced(true).build(),
                        ParamDef.builder()
                                .name("height").type(ParamType.INTEGER)
                                .displayName("自定义高度").min(1024).max(4992).step(2)
                                .defaultValue(2048)
                                .condition("size_preset=Custom")
                                .group("图片").advanced(true).build(),
                        ParamDef.builder()
                                .name("reference_images").type(ParamType.IMAGE_LIST)
                                .displayName("参考图片").description("最多 14 张（5.0）或 10 张（4.x）")
                                .max(14)
                                .group("参考").build(),
                        ParamDef.builder()
                                .name("max_images").type(ParamType.INTEGER)
                                .displayName("最大生成数").description("系列生成模式，1=单张")
                                .min(1).max(15).defaultValue(1)
                                .group("图片").build(),
                        ParamDef.builder()
                                .name("seed").type(ParamType.INTEGER)
                                .displayName("随机种子").min(0).max(2147483647L)
                                .defaultValue(0)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("watermark").type(ParamType.BOOLEAN)
                                .displayName("添加水印").defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("web_search").type(ParamType.BOOLEAN)
                                .displayName("联网搜索").description("生成前先搜索互联网获取参考资料")
                                .defaultValue(false)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("service_tier").type(ParamType.ENUM)
                                .displayName("服务层级").description("flex=离线推理半价，默认按需")
                                .options(List.of("flex"))
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("output_format").type(ParamType.ENUM)
                                .displayName("输出格式")
                                .options(List.of("png", "jpeg"))
                                .defaultValue("png")
                                .description("仅 5.0 Lite / 4.5 支持，4.0 不支持")
                                .group("图片").build(),
                        ParamDef.builder()
                                .name("optimize_prompt_mode").type(ParamType.ENUM)
                                .displayName("提示词优化")
                                .options(List.of("standard", "fast"))
                                .defaultValue("standard")
                                .description("fast 模式提速但质量略降（仅 4.0 支持）")
                                .condition("model=" + MODEL_SEEDREAM_40)
                                .group("高级").advanced(true).build(),
                        ParamDef.builder()
                                .name("response_format").type(ParamType.ENUM)
                                .displayName("返回方式")
                                .options(List.of("url", "b64_json"))
                                .defaultValue("url")
                                .group("高级").advanced(true).build()
                ))
                .outputFormats(List.of("png"))
                .estimatedDurationMs(30000)
                .costHint("约 $0.03~0.04/张")
                .build();
    }
}