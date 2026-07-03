package com.luciano.adapter.seedance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luciano.adapter.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

import static com.luciano.adapter.seedance.SeedanceConstants.*;

/**
 * Seedance (火山方舟) 适配器
 * <p>
 * 国内版走火山方舟平台（ark.cn-beijing.volces.com），Bearer Token 认证。
 * 国际版走 BytePlus（api.byteplus.com），认证方式相同。
 * <p>
 * 支持能力：
 * - TEXT_TO_VIDEO: 文生视频（1.x prompt 拼参数 + 2.0 JSON body）
 * - IMAGE_TO_VIDEO: 图生视频
 * - FIRST_LAST_FRAME: 首尾帧生成
 * - REFERENCE_TO_VIDEO: 多参考图/视频/音频生成（2.0 独有）
 * - TEXT_TO_IMAGE: 文生图（Seedream）
 * <p>
 * 架构验证点：
 * - 认证方式不同（Bearer Token vs Kling JWT）
 * - 计费方式不同（按 token vs 按次）
 * - 参数传递不同（1.x prompt 拼参数 vs 2.0 JSON body）
 * - 独有能力（REFERENCE_TO_VIDEO）Kling 不支持
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedanceAdapter implements ModelAdapter {

    private final SeedanceApiClient apiClient;
    private final SeedanceSchemaProvider schemaProvider;
    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return "seedance";
    }

    @Override
    public String getDisplayName() {
        return "Seedance";
    }

    @Override
    public String getDescription() {
        return "字节跳动 Seedance 视频生成 + Seedream 图片生成，支持多参考图/视频/音频";
    }

    @Override
    public Set<Capability> getCapabilities() {
        return EnumSet.of(
                Capability.TEXT_TO_VIDEO,
                Capability.IMAGE_TO_VIDEO,
                Capability.FIRST_LAST_FRAME,
                Capability.REFERENCE_TO_VIDEO,
                Capability.VIDEO_EDIT,
                Capability.VIDEO_EXTEND,
                Capability.TEXT_TO_IMAGE
        );
    }

    @Override
    public String getCostLevel() {
        return "MEDIUM";
    }

    @Override
    public CapabilitySchema getSchema(Capability capability) {
        return getSchema(capability, null);
    }

    @Override
    public CapabilitySchema getSchema(Capability capability, String model) {
        // 有 model 参数时，T2V 按版本返回对应 Schema（V1 含 frames，V2 含 return_last_frame 等）
        if (model != null) {
            return switch (capability) {
                case TEXT_TO_VIDEO -> isV2Model(model)
                        ? schemaProvider.textToVideoV2Schema()
                        : schemaProvider.textToVideoV1Schema();
                // I2V/FLF 已在单一 Schema 中包含所有模型选项，无需拆版本
                case IMAGE_TO_VIDEO -> schemaProvider.imageToVideoSchema();
                case FIRST_LAST_FRAME -> schemaProvider.firstLastFrameSchema();
                case REFERENCE_TO_VIDEO -> schemaProvider.referenceToVideoSchema();
                case VIDEO_EDIT -> schemaProvider.videoEditSchema();
                case VIDEO_EXTEND -> schemaProvider.videoExtendSchema();
                case TEXT_TO_IMAGE -> schemaProvider.textToImageSchema();
                default -> null;
            };
        }
        // 无 model 时返回默认 Schema
        return switch (capability) {
            case TEXT_TO_VIDEO -> schemaProvider.textToVideoV2Schema();
            case IMAGE_TO_VIDEO -> schemaProvider.imageToVideoSchema();
            case FIRST_LAST_FRAME -> schemaProvider.firstLastFrameSchema();
            case REFERENCE_TO_VIDEO -> schemaProvider.referenceToVideoSchema();
            case VIDEO_EDIT -> schemaProvider.videoEditSchema();
            case VIDEO_EXTEND -> schemaProvider.videoExtendSchema();
            case TEXT_TO_IMAGE -> schemaProvider.textToImageSchema();
            default -> null;
        };
    }

    // ==================== 参数校验 ====================

    @Override
    public ValidationResult validate(Capability capability, Map<String, Object> params) {
        if (params == null) return ValidationResult.errors("参数不能为空");

        List<String> errors = new ArrayList<>();
        switch (capability) {
            case TEXT_TO_VIDEO -> validateTextToVideo(params, errors);
            case IMAGE_TO_VIDEO -> validateImageToVideo(params, errors);
            case FIRST_LAST_FRAME -> validateFirstLastFrame(params, errors);
            case REFERENCE_TO_VIDEO -> validateReferenceToVideo(params, errors);
            case VIDEO_EDIT -> validateVideoEdit(params, errors);
            case VIDEO_EXTEND -> validateVideoExtend(params, errors);
            case TEXT_TO_IMAGE -> validateTextToImage(params, errors);
            default -> errors.add("不支持的能力: " + capability.getCode());
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.errors(errors);
    }

    private void validateTextToVideo(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) errors.add("提示词不能为空");

        String[] reserved = {"resolution", "ratio", "duration", "seed", "camerafixed", "watermark"};
        for (String rp : reserved) {
            if (prompt != null && prompt.contains("--" + rp + " ")) {
                errors.add("提示词不能包含 --" + rp + "，请使用对应参数");
            }
        }

        String model = getStr(params, "model");
        Integer duration = getInt(params, "duration");
        if (MODEL_SD15_PRO.equals(model) && duration != null && duration < 4) {
            errors.add("Seedance 1.5 Pro 最短 4 秒");
        }
    }

    private void validateImageToVideo(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) errors.add("提示词不能为空");
        boolean hasImage = params.containsKey("image") || params.containsKey("image_url")
                || params.containsKey("image_list");
        log.warn("[validateImageToVideo] hasImage={}, keys={}, image_list={}", hasImage, params.keySet(), params.get("image_list"));
        if (!hasImage) errors.add("起始帧图片不能为空");
    }

    private void validateFirstLastFrame(Map<String, Object> params, List<String> errors) {
        boolean hasFirstFrame = params.containsKey("first_frame") || params.containsKey("first_frame_url")
                || params.containsKey("first_frame_list");
        if (!hasFirstFrame) errors.add("首帧图片不能为空");
    }

    private void validateReferenceToVideo(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) errors.add("提示词不能为空");

        boolean hasRef = params.containsKey("reference_images") || params.containsKey("reference_videos")
                || params.containsKey("reference_audios")
                || params.containsKey("reference_images_list") || params.containsKey("reference_videos_list")
                || params.containsKey("reference_audios_list");
        if (!hasRef) errors.add("至少需要一个参考图片、参考视频或参考音频");

        if (getListSize(params, "reference_images") + getListSize(params, "reference_images_list") > MAX_REFERENCE_IMAGES) errors.add("参考图片最多 " + MAX_REFERENCE_IMAGES + " 张");
        if (getListSize(params, "reference_videos") + getListSize(params, "reference_videos_list") > MAX_REFERENCE_VIDEOS) errors.add("参考视频最多 " + MAX_REFERENCE_VIDEOS + " 个");
        if (getListSize(params, "reference_audios") + getListSize(params, "reference_audios_list") > MAX_REFERENCE_AUDIOS) errors.add("参考音频最多 " + MAX_REFERENCE_AUDIOS + " 个");
    }

    private void validateVideoEdit(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) errors.add("提示词不能为空");
        // 视频编辑需要至少一个参考视频
        boolean hasVideo = params.containsKey("reference_videos") || params.containsKey("reference_videos_list");
        if (!hasVideo) errors.add("视频编辑需要至少一个参考视频");
        if (getListSize(params, "reference_videos") + getListSize(params, "reference_videos_list") > MAX_REFERENCE_VIDEOS) errors.add("参考视频最多 " + MAX_REFERENCE_VIDEOS + " 个");
    }

    private void validateVideoExtend(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) errors.add("提示词不能为空");
        boolean hasVideo = params.containsKey("reference_videos") || params.containsKey("reference_videos_list");
        if (!hasVideo) errors.add("延长视频需要至少一个参考视频");
        int videoCount = getListSize(params, "reference_videos") + getListSize(params, "reference_videos_list");
        if (videoCount > 3) errors.add("延长视频最多 3 个视频片段");
    }

    private void validateTextToImage(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) errors.add("提示词不能为空");
    }

    // ==================== 提交任务 ====================

    @Override
    public TaskHandle submit(Capability capability, Map<String, Object> params) {
        return switch (capability) {
            case TEXT_TO_VIDEO -> submitTextToVideo(params);
            case IMAGE_TO_VIDEO -> submitImageToVideo(params);
            case FIRST_LAST_FRAME -> submitFirstLastFrame(params);
            case REFERENCE_TO_VIDEO -> submitReferenceToVideo(params);
            case VIDEO_EDIT -> submitVideoEdit(params);
            case VIDEO_EXTEND -> submitVideoExtend(params);
            case TEXT_TO_IMAGE -> submitTextToImage(params);
            default -> throw new UnsupportedOperationException("Seedance 不支持能力: " + capability.getCode());
        };
    }

    private TaskHandle submitTextToVideo(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        return isV2Model(model) ? submitTextToVideoV2(params) : submitTextToVideoV1(params);
    }

    /** Seedance 1.x: 参数拼在 prompt 里 */
    private TaskHandle submitTextToVideoV1(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD10_PRO_FAST);
        String prompt = getStr(params, "prompt");
        String resolution = getStr(params, "resolution", "720p");
        String aspectRatio = getStr(params, "aspect_ratio", "16:9");
        int duration = getInt(params, "duration", 5);
        Integer frames = getInt(params, "frames");
        long seed = getLong(params, "seed", 0L);
        boolean cameraFixed = getBool(params, "camera_fixed", false);
        boolean watermark = getBool(params, "watermark", false);
        boolean generateAudio = getBool(params, "generate_audio", false);
        boolean draft = getBool(params, "draft", false);

        // frames 优先于 duration（仅 1.x 系列支持 frames）
        String durationPart = frames != null
                ? "--frames " + frames
                : "--duration " + duration;

        String fullPrompt = prompt + " --resolution " + resolution + " --ratio " + aspectRatio
                + " " + durationPart + " --seed " + seed
                + " --camerafixed " + cameraFixed + " --watermark " + watermark;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        content.addObject().put("type", "text").put("text", fullPrompt);
        if (MODEL_SD15_PRO.equals(model) && generateAudio) body.put("generate_audio", true);
        // draft 仅 1.5 Pro 支持；draft=true 时强制 480p，忽略 return_last_frame
        if (MODEL_SD15_PRO.equals(model) && draft) {
            body.put("draft", true);
            log.info("[Seedance] T2V V1 draft 模式: 480p 预览，不返回尾帧");
        }

        int estDuration = draft ? 30 : estimateDurationV1(model, resolution, duration);
        return executeTask(PATH_TASK, body, Capability.TEXT_TO_VIDEO, estDuration);
    }

    /** Seedance 2.0: JSON body 传参 */
    private TaskHandle submitTextToVideoV2(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        String prompt = getStr(params, "prompt");
        String resolution = getStr(params, "resolution", "720p");
        String ratio = getStr(params, "aspect_ratio", "16:9");
        int duration = getInt(params, "duration", DURATION_DEFAULT_V2);
        boolean generateAudio = getBool(params, "generate_audio", true);
        Long seed = getLongObj(params, "seed");
        boolean watermark = getBool(params, "watermark", false);
        boolean draft = getBool(params, "draft", false);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        content.addObject().put("type", "text").put("text", prompt);
        body.put("resolution", resolution).put("ratio", ratio).put("duration", duration);
        body.put("generate_audio", generateAudio).put("watermark", watermark);
        if (seed != null) body.put("seed", seed);
        // draft=true 时不支持 return_last_frame（官方文档限制）
        if (!draft && getBool(params, "return_last_frame", false)) body.put("return_last_frame", true);
        if (getBool(params, "camera_fixed", false)) body.put("camera_fixed", true);
        if (getBool(params, "web_search", false)) { ArrayNode tools = body.putArray("tools"); tools.addObject().put("type", "web_search"); }
        String serviceTier = getStr(params, "service_tier", null); if (serviceTier != null) body.put("service_tier", serviceTier);
        // draft 仅 1.5 Pro 支持，2.0 系列不支持但传了也不报错（API 会忽略）
        if (draft) {
            log.warn("[Seedance] T2V V2 draft 模式仅 1.5 Pro 支持，当前模型={}", model);
        }

        return executeTask(PATH_TASK, body, Capability.TEXT_TO_VIDEO, estimateDurationV2(resolution, duration));
    }

    private TaskHandle submitImageToVideo(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        return isV2Model(model) ? submitImageToVideoV2(params) : submitImageToVideoV1(params);
    }

    /** Seedance 1.x I2V: 参数拼在 prompt 里 */
    private TaskHandle submitImageToVideoV1(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD10_PRO_FAST);
        String prompt = getStr(params, "prompt");
        String resolution = getStr(params, "resolution", "720p");
        String aspectRatio = getStr(params, "aspect_ratio", "adaptive");
        int duration = getInt(params, "duration", 5);
        Integer frames = getInt(params, "frames");
        boolean cameraFixed = getBool(params, "camera_fixed", false);
        boolean watermark = getBool(params, "watermark", false);
        boolean draft = getBool(params, "draft", false);

        // frames 优先于 duration（仅 1.x 系列支持 frames）
        String durationPart = frames != null
                ? "--frames " + frames
                : "--duration " + duration;

        String fullPrompt = prompt + " --resolution " + resolution + " --ratio " + aspectRatio
                + " " + durationPart + " --seed " + getLong(params, "seed", 0L)
                + " --camerafixed " + cameraFixed + " --watermark " + watermark;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        content.addObject().put("type", "text").put("text", fullPrompt);
        // 同时支持 Schema 参数名(image)、_url 后缀名和 multi 端口收集的 image_list
        String imageUrl = getStr(params, "image", getStr(params, "image_url", null));
        if (imageUrl == null) {
            // multi 端口连线收集到 image_list，取第一张作为首帧
            List<?> imageList = getList(params, "image_list");
            if (imageList != null && !imageList.isEmpty()) {
                Object first = imageList.get(0);
                if (first instanceof String s) imageUrl = s;
                else if (first instanceof Map<?, ?> m && m.get("url") instanceof String s) imageUrl = s;
            }
        }
        if (imageUrl != null) {
            addImageUrlToContent(content, imageUrl, null);
        }
        if (MODEL_SD15_PRO.equals(model) && getBool(params, "generate_audio", false)) {
            body.put("generate_audio", true);
        }
        // draft 仅 1.5 Pro 支持
        if (MODEL_SD15_PRO.equals(model) && draft) {
            body.put("draft", true);
            log.info("[Seedance] I2V V1 draft 模式: 480p 预览");
        }

        int estDuration = draft ? 30 : estimateDurationV1(model, resolution, duration);
        return executeTask(PATH_TASK, body, Capability.IMAGE_TO_VIDEO, estDuration);
    }

    /** Seedance 2.0 I2V: JSON body 传参，支持多图参考 */
    private TaskHandle submitImageToVideoV2(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        String prompt = getStr(params, "prompt");
        String resolution = getStr(params, "resolution", "720p");
        String ratio = getStr(params, "aspect_ratio", "adaptive");
        int duration = getInt(params, "duration", DURATION_DEFAULT_V2);
        boolean generateAudio = getBool(params, "generate_audio", true);
        Long seed = getLongObj(params, "seed");
        boolean watermark = getBool(params, "watermark", false);
        boolean draft = getBool(params, "draft", false);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        if (prompt != null && !prompt.isBlank()) {
            content.addObject().put("type", "text").put("text", prompt);
        }
        
        // 收集所有图片 URL：multi 端口自动收集成 image_list，手动填写的在 image 字段
        List<String> imageUrls = new ArrayList<>();
        // image_list 来自 multi 端口连线收集
        List<?> imageList = getList(params, "image_list");
        if (imageList != null) {
            for (Object item : imageList) {
                if (item instanceof String s) imageUrls.add(s);
                else if (item instanceof Map<?, ?> m && m.get("url") instanceof String s) imageUrls.add(s);
            }
        }
        // image 来自手动填写或单连线；也可能是数组（前端传多图时直接用 image 字段）
        Object imageParam = params.get("image");
        if (imageParam instanceof List<?> list) {
            // image 参数是数组 — 逐个提取 URL
            for (Object item : list) {
                if (item instanceof String s && !imageUrls.contains(s)) imageUrls.add(s);
                else if (item instanceof Map<?, ?> m && m.get("url") instanceof String s && !imageUrls.contains(s)) imageUrls.add(s);
            }
        } else {
            String singleImage = getStr(params, "image", getStr(params, "image_url", null));
            if (singleImage != null && !imageUrls.contains(singleImage)) {
                imageUrls.add(0, singleImage);
            }
        }
        
        // 单图作为首帧（无 role），多图全部带 reference_image role（Python E2E 验证格式）
        if (imageUrls.size() == 1) {
            addImageUrlToContent(content, imageUrls.get(0), null); // 首帧
        } else {
            for (String url : imageUrls) {
                addImageUrlToContent(content, url, "reference_image");
            }
        }
        
        body.put("resolution", resolution).put("ratio", ratio).put("duration", duration);
        body.put("generate_audio", generateAudio).put("watermark", watermark);
        if (seed != null) body.put("seed", seed);
        // draft=true 时不支持 return_last_frame（官方文档限制）
        if (!draft && getBool(params, "return_last_frame", false)) body.put("return_last_frame", true);
        if (getBool(params, "camera_fixed", false)) body.put("camera_fixed", true);
        if (draft) {
            log.warn("[Seedance] I2V V2 draft 模式仅 1.5 Pro 支持，当前模型={}", model);
        }

        log.info("[Seedance] I2V 2.0 提交: model={}, images={}, duration={}s", model, imageUrls.size(), duration);
        return executeTask(PATH_TASK, body, Capability.IMAGE_TO_VIDEO, estimateDurationV2(resolution, duration));
    }

    private TaskHandle submitFirstLastFrame(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        return isV2Model(model) ? submitFirstLastFrameV2(params) : submitFirstLastFrameV1(params);
    }

    private TaskHandle submitFirstLastFrameV1(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD10_LITE_I2V);
        String prompt = getStr(params, "prompt", "");
        String resolution = getStr(params, "resolution", "720p");
        String aspectRatio = getStr(params, "aspect_ratio", "adaptive");
        int duration = getInt(params, "duration", 5);
        boolean draft = getBool(params, "draft", false);

        String fullPrompt = prompt + " --resolution " + resolution + " --ratio " + aspectRatio
                + " --duration " + duration + " --seed " + getLong(params, "seed", 0L)
                + " --camerafixed " + getBool(params, "camera_fixed", false)
                + " --watermark " + getBool(params, "watermark", false);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        content.addObject().put("type", "text").put("text", fullPrompt);

        String firstFrameUrl = getStr(params, "first_frame", getStr(params, "first_frame_url", null));
        String lastFrameUrl = getStr(params, "last_frame", getStr(params, "last_frame_url", null));

        if (firstFrameUrl != null) {
            addImageUrlToContent(content, firstFrameUrl, "first_frame");
        }
        if (lastFrameUrl != null) {
            addImageUrlToContent(content, lastFrameUrl, "last_frame");
        }

        // draft 仅 1.5 Pro 支持
        if (MODEL_SD15_PRO.equals(model) && draft) {
            body.put("draft", true);
            log.info("[Seedance] FLF V1 draft 模式: 480p 预览");
        }
        int estDuration = draft ? 30 : estimateDurationV1(model, resolution, duration);
        return executeTask(PATH_TASK, body, Capability.FIRST_LAST_FRAME, estDuration);
    }

    private TaskHandle submitFirstLastFrameV2(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        String prompt = getStr(params, "prompt", "");
        String resolution = getStr(params, "resolution", "720p");
        String ratio = getStr(params, "aspect_ratio", "adaptive");
        int duration = getInt(params, "duration", DURATION_DEFAULT_V2);
        boolean generateAudio = getBool(params, "generate_audio", true);
        Long seed = getLongObj(params, "seed");
        boolean watermark = getBool(params, "watermark", false);
        boolean draft = getBool(params, "draft", false);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        content.addObject().put("type", "text").put("text", prompt);

        String firstFrameUrl = getStr(params, "first_frame", getStr(params, "first_frame_url", null));
        String lastFrameUrl = getStr(params, "last_frame", getStr(params, "last_frame_url", null));

        if (firstFrameUrl != null) {
            addImageUrlToContent(content, firstFrameUrl, "first_frame");
        }
        if (lastFrameUrl != null) {
            addImageUrlToContent(content, lastFrameUrl, "last_frame");
        }

        body.put("resolution", resolution).put("ratio", ratio).put("duration", duration);
        body.put("generate_audio", generateAudio).put("watermark", watermark);
        if (seed != null) body.put("seed", seed);
        if (getBool(params, "camera_fixed", false)) body.put("camera_fixed", true);
        if (draft) {
            log.warn("[Seedance] FLF V2 draft 模式仅 1.5 Pro 支持，当前模型={}", model);
        }

        return executeTask(PATH_TASK, body, Capability.FIRST_LAST_FRAME, estimateDurationV2(resolution, duration));
    }

    /** Seedance 2.0 独有：多参考生成 */
    private TaskHandle submitReferenceToVideo(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        String prompt = getStr(params, "prompt");
        String resolution = getStr(params, "resolution", "720p");
        String ratio = getStr(params, "aspect_ratio", "adaptive");
        int duration = getInt(params, "duration", DURATION_DEFAULT_V2);
        boolean generateAudio = getBool(params, "generate_audio", true);
        Long seed = getLongObj(params, "seed");
        boolean watermark = getBool(params, "watermark", false);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        content.addObject().put("type", "text").put("text", prompt);

        addReferenceUrls(content, params);

        body.put("resolution", resolution).put("ratio", ratio).put("duration", duration);
        body.put("generate_audio", generateAudio).put("watermark", watermark);
        if (seed != null) body.put("seed", seed);
        if (getBool(params, "camera_fixed", false)) body.put("camera_fixed", true);

        return executeTask(PATH_TASK, body, Capability.REFERENCE_TO_VIDEO, estimateDurationV2(resolution, duration));
    }

    /** Seedance 2.0 独有：视频编辑 */
    private TaskHandle submitVideoEdit(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        String prompt = getStr(params, "prompt");
        String resolution = getStr(params, "resolution", "720p");
        String ratio = getStr(params, "aspect_ratio", "adaptive");
        int duration = getInt(params, "duration", DURATION_DEFAULT_V2);
        boolean generateAudio = getBool(params, "generate_audio", true);
        Long seed = getLongObj(params, "seed");
        boolean watermark = getBool(params, "watermark", false);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        if (prompt != null && !prompt.isBlank()) {
            content.addObject().put("type", "text").put("text", prompt);
        }
        // 参考图 + 参考视频通过通用 addReferenceUrls 注入
        addReferenceUrls(content, params);
        body.put("resolution", resolution).put("ratio", ratio).put("duration", duration);
        body.put("generate_audio", generateAudio).put("watermark", watermark);
        if (seed != null) body.put("seed", seed);

        if (getBool(params, "camera_fixed", false)) body.put("camera_fixed", true);
        log.info("[Seedance] VIDEO_EDIT 提交: model={}, duration={}s", model, duration);
        return executeTask(PATH_TASK, body, Capability.VIDEO_EDIT, estimateDurationV2(resolution, duration));
    }

    /** Seedance 2.0 独有：延长视频（最多3段串联） */
    private TaskHandle submitVideoExtend(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SD20);
        String prompt = getStr(params, "prompt");
        int duration = getInt(params, "duration", DURATION_DEFAULT_V2);
        boolean generateAudio = getBool(params, "generate_audio", true);
        Long seed = getLongObj(params, "seed");
        boolean watermark = getBool(params, "watermark", false);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode content = body.putArray("content");
        if (prompt != null && !prompt.isBlank()) {
            content.addObject().put("type", "text").put("text", prompt);
        }
        // 参考视频通过 addReferenceUrls 注入（reference_videos_list → role: reference_video）
        addReferenceUrls(content, params);
        body.put("duration", duration);
        body.put("generate_audio", generateAudio).put("watermark", watermark);
        if (seed != null) body.put("seed", seed);
        // 延长视频不需要 resolution/ratio（继承源视频）
        if (getBool(params, "camera_fixed", false)) body.put("camera_fixed", true);

        log.info("[Seedance] VIDEO_EXTEND 提交: model={}, duration={}s", model, duration);
        return executeTask(PATH_TASK, body, Capability.VIDEO_EXTEND, estimateDurationV2("720p", duration));
    }

    /** 方舟 API 正确的图片 content 格式：{"type": "image_url", "image_url": {"url": "..."}, "role": "..."} */
    private void addImageUrlToContent(ArrayNode content, String url, String role) {
        ObjectNode imgNode = content.addObject();
        imgNode.put("type", "image_url");
        ObjectNode imageUrl = imgNode.putObject("image_url");
        imageUrl.put("url", url);
        if (role != null) imgNode.put("role", role);
    }

    /** 方舟 API 正确的视频 content 格式：{"type": "video_url", "video_url": {"url": "..."}, "role": "reference_video"} */
    private void addVideoUrlToContent(ArrayNode content, String url, String role) {
        ObjectNode vidNode = content.addObject();
        vidNode.put("type", "video_url");
        ObjectNode videoUrl = vidNode.putObject("video_url");
        videoUrl.put("url", url);
        if (role != null) vidNode.put("role", role);
    }

    /** 方舟 API 正确的音频 content 格式：{"type": "audio_url", "audio_url": {"url": "..."}} */
    private void addAudioUrlToContent(ArrayNode content, String url) {
        ObjectNode audNode = content.addObject();
        audNode.put("type", "audio_url");
        ObjectNode audioUrl = audNode.putObject("audio_url");
        audioUrl.put("url", url);
    }

    @SuppressWarnings("unchecked")
    private void addReferenceUrls(ArrayNode content, Map<String, Object> params) {
        // multi 端口自动收集成 _list 后缀；同时兼容 Schema 参数名（不带 _list）
        addReferenceUrlsFromList(content, params, "reference_images_list", "reference_images", "reference_image_urls", "reference_image");
        addReferenceUrlsFromListVideo(content, params, "reference_videos_list", "reference_videos", "reference_video_urls", "reference_video");
        addReferenceUrlsFromListAudio(content, params, "reference_audios_list", "reference_audios", "reference_audio_urls");
    }
    
    private void addReferenceUrlsFromList(ArrayNode content, Map<String, Object> params,
                                           String listKey, String schemaKey, String legacyKey, String role) {
        List<String> urls = collectUrls(params, listKey, schemaKey, legacyKey);
        for (String url : urls) {
            addImageUrlToContent(content, url, role);
        }
    }
    
    private void addReferenceUrlsFromListVideo(ArrayNode content, Map<String, Object> params,
                                               String listKey, String schemaKey, String legacyKey, String role) {
        List<String> urls = collectUrls(params, listKey, schemaKey, legacyKey);
        for (String url : urls) {
            addVideoUrlToContent(content, url, role);
        }
    }
    
    private void addReferenceUrlsFromListAudio(ArrayNode content, Map<String, Object> params,
                                               String listKey, String schemaKey, String legacyKey) {
        List<String> urls = collectUrls(params, listKey, schemaKey, legacyKey);
        for (String url : urls) {
            addAudioUrlToContent(content, url);
        }
    }
    
    /** 从 _list 参数、Schema 参数名和旧参数名收集 URL */
    @SuppressWarnings("unchecked")
    private List<String> collectUrls(Map<String, Object> params, String listKey, String schemaKey, String legacyKey) {
        List<String> urls = new ArrayList<>();
        // multi 端口收集（_list 后缀）
        List<?> list = getList(params, listKey);
        if (list != null) {
            for (Object item : list) {
                if (item instanceof String s && !urls.contains(s)) urls.add(s);
                else if (item instanceof Map<?, ?> m && m.get("url") instanceof String s && !urls.contains(s)) urls.add(s);
            }
        }
        // Schema 定义的参数名（IMAGE_LIST/VIDEO_LIST 类型，前端直接传数组）
        List<?> schemaList = getList(params, schemaKey);
        if (schemaList != null) {
            for (Object item : schemaList) {
                if (item instanceof String s && !urls.contains(s)) urls.add(s);
                else if (item instanceof Map<?, ?> m && m.get("url") instanceof String s && !urls.contains(s)) urls.add(s);
            }
        }
        // 兼容旧参数名
        List<String> legacy = (List<String>) params.get(legacyKey);
        if (legacy != null) {
            for (String s : legacy) {
                if (!urls.contains(s)) urls.add(s);
            }
        }
        return urls;
    }

    private TaskHandle submitTextToImage(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SEEDREAM_5_LITE);
        String prompt = getStr(params, "prompt");
        String sizePreset = getStr(params, "size_preset", "2048×2048");
        Integer maxImages = getInt(params, "max_images", 1);
        Long seed = getLongObj(params, "seed");
        boolean watermark = getBool(params, "watermark", false);

        // 兼容直接传 size 参数（如 "2K"），优先于 size_preset
        String directSize = getStr(params, "size", null);
        String size = directSize != null
                ? directSize
                : ("Custom".equals(sizePreset)
                    ? getInt(params, "width", 2048) + "x" + getInt(params, "height", 2048)
                    : sizePreset.replace("×", "x"));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model).put("prompt", prompt).put("size", size).put("watermark", watermark);
        if (seed != null) body.put("seed", seed);
        // 输出格式：仅 5.0 Lite / 4.5 支持，4.0 不支持会报 400
        String outputFormat = getStr(params, "output_format", null);
        if (outputFormat != null && !MODEL_SEEDREAM_40.equals(model)) {
            body.put("output_format", outputFormat);
        }
        // 提示词优化模式（仅 4.0 支持）
        String optimizeMode = getStr(params, "optimize_prompt_mode", null);
        if (optimizeMode != null && MODEL_SEEDREAM_40.equals(model)) {
            body.putObject("optimize_prompt_options").put("mode", optimizeMode);
        }
        String responseFormat = getStr(params, "response_format", null);
        if (responseFormat != null) body.put("response_format", responseFormat);
        if (getBool(params, "web_search", false)) { ArrayNode tools = body.putArray("tools"); tools.addObject().put("type", "web_search"); }
        String serviceTier = getStr(params, "service_tier", null); if (serviceTier != null) body.put("service_tier", serviceTier);

        @SuppressWarnings("unchecked")
        // image_list 来自 multi 端口连线收集
        List<?> imageList = getList(params, "image_list");
        List<String> imageUrls = new ArrayList<>();
        if (imageList != null) {
            for (Object item : imageList) {
                if (item instanceof String s) imageUrls.add(s);
                else if (item instanceof Map<?, ?> m && m.get("url") instanceof String s) imageUrls.add(s);
            }
        }
        // 兼容旧参数名 reference_image_urls
        List<String> legacyUrls = (List<String>) params.get("reference_image_urls");
        if (legacyUrls != null) imageUrls.addAll(legacyUrls);
        
        if (!imageUrls.isEmpty()) {
            if (imageUrls.size() == 1) {
                body.put("image", imageUrls.get(0)); // 单图：string
            } else {
                ArrayNode imgArray = body.putArray("image"); // 多图：array
                imageUrls.forEach(imgArray::add);
            }
        }

        if (maxImages != null && maxImages > 1) {
            body.put("sequential_image_generation", "auto");
            body.putObject("sequential_image_generation_options").put("max_images", maxImages);
        }

        JsonNode response = apiClient.post(PATH_IMAGE, body, JsonNode.class);
        List<String> resultImageUrls = extractImageUrls(response);

        // Seedream 同步返回 URL — 第一张存 resultUrl，多图额外存 resultUrls
        TaskHandle.TaskHandleBuilder builder = TaskHandle.builder()
                .taskId("seedance:img_" + System.currentTimeMillis())
                .adapterId(getId())
                .capability(Capability.TEXT_TO_IMAGE)
                .resultUrl(resultImageUrls.get(0))
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now());
        if (resultImageUrls.size() > 1) {
            builder.resultUrls(resultImageUrls);
            log.info("[Seedance] 组图生成返回 {} 张图片", resultImageUrls.size());
        }
        return builder.build();
    }

    // ==================== 轮询 & 下载 ====================

    @Override
    public TaskStatus poll(TaskHandle handle) {
        if (handle.getProviderTaskId() == null && handle.getResultUrl() != null) return TaskStatus.COMPLETED;

        try {
            JsonNode response = apiClient.get(determinePollPath(handle), JsonNode.class);
            String status = extractTaskStatus(response);

            // 回填平台原始状态，供操作日志使用
            handle.setPlatformStatus(status);

            return switch (status) {
                case TASK_STATUS_SUCCEEDED -> TaskStatus.COMPLETED;
                case TASK_STATUS_FAILED, TASK_STATUS_EXPIRED -> TaskStatus.FAILED;
                case TASK_STATUS_RUNNING -> TaskStatus.PROCESSING;
                default -> TaskStatus.PENDING; // queued → PENDING
            };
        } catch (Exception e) {
            log.error("[SeedanceAdapter] Poll failed: {}", e.getMessage());
            return TaskStatus.PROCESSING;
        }
    }

    @Override
    public MediaResult download(TaskHandle handle) {
        if (handle.getResultUrl() != null) {
            // 图片生成同步返回 — 检查是否有组图
            MediaResult.MediaResultBuilder b = MediaResult.builder()
                    .mediaType("image")
                    .originalUrl(handle.getResultUrl());
            if (handle.getResultUrls() != null && handle.getResultUrls().size() > 1) {
                // 组图：第一张已作为 originalUrl，剩余的放 additionalUrls
                b.additionalUrls(handle.getResultUrls().subList(1, handle.getResultUrls().size()));
            }
            return b.build();
        }
        try {
            JsonNode response = apiClient.get(determinePollPath(handle), JsonNode.class);
            String url = extractVideoUrl(response);
            // 提取尾帧 URL（return_last_frame=true 时 Ark API 返回 content.last_frame_url）
            String lastFrameUrl = null;
            if (response.has("content") && response.get("content").has("last_frame_url")) {
                lastFrameUrl = response.get("content").get("last_frame_url").asText();
            }
            MediaResult.MediaResultBuilder b = MediaResult.builder().mediaType("video").originalUrl(url);
            if (lastFrameUrl != null) {
                b.additionalUrls(java.util.List.of(lastFrameUrl));
                b.metadata(java.util.Map.of("last_frame_url", lastFrameUrl));
            }
            return b.build();
        } catch (Exception e) {
            throw new RuntimeException("Seedance 下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancel(TaskHandle handle) {
        log.warn("[SeedanceAdapter] Cancel not supported: {}", handle.getTaskId());
    }

    // ==================== 费用预估 ====================

    @Override
    public CostEstimate estimateCost(Capability capability, Map<String, Object> params) {
        if (capability == Capability.TEXT_TO_IMAGE) return estimateImageCost(params);

        String model = getStr(params, "model", MODEL_SD20);
        String resolution = getStr(params, "resolution", "720p");
        int duration = getInt(params, "duration", 5);

        return isV2Model(model) ? estimateV2Cost(model, resolution, duration, params) : estimateV1Cost(model, resolution, duration);
    }

    private CostEstimate estimateV1Cost(String model, String resolution, int duration) {
        String modelKey = model != null ? model : MODEL_SD10_PRO_FAST;
        String resKey = normalizeResolution(resolution);

        java.util.Map<String, double[]> resPrices = V1_PRICES.getOrDefault(modelKey, V1_PRICES.get(MODEL_SD10_PRO_FAST));
        double[] prices = resPrices.getOrDefault(resKey, resPrices.getOrDefault("720p", new double[]{0.21}));
        double cost = prices[0] * duration / 10.0;

        return CostEstimate.builder().amount(java.math.BigDecimal.valueOf(cost)).currency("USD")
                .billingModel("按次计费").displayText(String.format("约 $%.2f/次", cost))
                .estimatedDurationMs(estimateDurationV1(modelKey, resKey, duration)).build();
    }

    private CostEstimate estimateV2Cost(String model, String resolution, int duration, Map<String, Object> params) {
        String resKey = normalizeResolution(resolution);
        int rate = TOKEN_RATE.getOrDefault(resKey, 21600);
        boolean hasVideo = params.containsKey("reference_video_urls");
        double pricePer1K = hasVideo
                ? (MODEL_SD20_FAST.equals(model) ? SD20_FAST_VIDEO_PRICE_PER_1K_TOKENS : SD20_VIDEO_PRICE_PER_1K_TOKENS)
                : (MODEL_SD20_FAST.equals(model) ? SD20_FAST_PRICE_PER_1K_TOKENS : SD20_PRICE_PER_1K_TOKENS);

        double cost = duration * rate * pricePer1K / 1000.0;
        String hint = hasVideo
                ? String.format("约 $%.2f~%.2f/次", Math.ceil(duration * 5.0 / 3) * rate * pricePer1K / 1000.0, (15 + duration) * rate * pricePer1K / 1000.0)
                : String.format("约 $%.2f/次", cost);

        return CostEstimate.builder().amount(java.math.BigDecimal.valueOf(cost)).currency("USD")
                .billingModel("按 Token 计费").displayText(hint)
                .estimatedDurationMs(estimateDurationV2(resKey, duration)).build();
    }

    private CostEstimate estimateImageCost(Map<String, Object> params) {
        String model = getStr(params, "model", MODEL_SEEDREAM_5_LITE);
        double price = MODEL_SEEDREAM_45.equals(model) ? COST_SEEDREAM_45
                : MODEL_SEEDREAM_5_LITE.equals(model) ? COST_SEEDREAM_5_LITE : COST_SEEDREAM_40;

        return CostEstimate.builder().amount(java.math.BigDecimal.valueOf(price)).currency("USD")
                .billingModel("按次计费").displayText(String.format("约 $%.3f/张", price))
                .estimatedDurationMs(30000).build();
    }

    // ==================== 私有辅助 ====================

    private TaskHandle executeTask(String path, ObjectNode body, Capability capability, int estimatedMs) {
        JsonNode response = apiClient.post(path, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("seedance:" + taskId).adapterId(getId()).capability(capability)
                .providerTaskId(taskId).createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(estimatedMs)))
                .build();
    }

    private boolean isV2Model(String model) {
        return MODEL_SD20.equals(model) || MODEL_SD20_FAST.equals(model);
    }

    private String determinePollPath(TaskHandle handle) {
        return PATH_TASK_STATUS + "/" + handle.getProviderTaskId();
    }

    private String extractTaskId(JsonNode response) {
        if (response.has("id")) return response.get("id").asText();
        throw new RuntimeException("Seedance 返回缺少 task id: " + response);
    }

    private String extractTaskStatus(JsonNode response) {
        return response.has("status") ? response.get("status").asText() : TASK_STATUS_SUBMITTED;
    }

    private String extractVideoUrl(JsonNode response) {
        return response.has("content") && response.get("content").has("video_url")
                ? response.get("content").get("video_url").asText() : null;
    }

    private String extractImageUrl(JsonNode response) {
        List<String> urls = extractImageUrls(response);
        return urls.isEmpty() ? null : urls.get(0);
    }

    /** 从图片 API 响应中提取所有图片 URL（组图模式返回多张） */
    private List<String> extractImageUrls(JsonNode response) {
        List<String> urls = new ArrayList<>();
        if (response.has("data") && response.get("data").isArray()) {
            for (JsonNode item : response.get("data")) {
                if (item.has("url")) urls.add(item.get("url").asText());
                // b64_json 格式：data[].b64_json
                if (item.has("b64_json")) urls.add("data:image/png;base64," + item.get("b64_json").asText());
            }
        }
        if (urls.isEmpty()) {
            throw new RuntimeException("Seedance 图片返回缺少 URL: " + response);
        }
        return urls;
    }

    private int estimateDurationV1(String model, String resolution, int duration) {
        // duration=-1 智能时长，用默认 5 秒估算
        int effectiveDuration = duration <= 0 ? 5 : duration;
        String resKey = normalizeResolution(resolution);
        java.util.Map<String, Integer> resTimes = V1_EXEC_TIME.getOrDefault(model, V1_EXEC_TIME.get(MODEL_SD10_PRO_FAST));
        int baseTime = resTimes.getOrDefault(resKey, 200);
        return Math.max(1, (int) Math.ceil(baseTime * effectiveDuration / 10.0)) * 1000;
    }

    private int estimateDurationV2(String resolution, int duration) {
        // duration=-1 智能时长，用默认 7 秒估算
        int effectiveDuration = duration <= 0 ? DURATION_DEFAULT_V2 : duration;
        int baseTime = switch (normalizeResolution(resolution)) { case "1080p" -> 440; case "720p" -> 200; default -> 90; };
        return Math.max(1, (int) Math.ceil(baseTime * effectiveDuration / 10.0)) * 1000;
    }

    private String normalizeResolution(String r) {
        if (r == null) return "720p";
        if (r.contains("1080")) return "1080p";
        if (r.contains("720")) return "720p";
        return "480p";
    }

    private String getStr(Map<String, Object> p, String k) { Object v = p.get(k); return v != null ? v.toString() : null; }
    private String getStr(Map<String, Object> p, String k, String d) { Object v = p.get(k); return v != null ? v.toString() : d; }
    private Integer getInt(Map<String, Object> p, String k) { Object v = p.get(k); if (v instanceof Number n) return n.intValue(); if (v instanceof String s) try { return Integer.parseInt(s); } catch (Exception e) { return null; } return null; }
    private Integer getInt(Map<String, Object> p, String k, int d) { Integer v = getInt(p, k); return v != null ? v : d; }
    private Long getLongObj(Map<String, Object> p, String k) { Object v = p.get(k); if (v instanceof Number n) return n.longValue(); if (v instanceof String s) try { return Long.parseLong(s); } catch (Exception e) { return null; } return null; }
    private long getLong(Map<String, Object> p, String k, long d) { Long v = getLongObj(p, k); return v != null ? v : d; }
    private boolean getBool(Map<String, Object> p, String k, boolean d) { Object v = p.get(k); if (v instanceof Boolean b) return b; if (v instanceof String s) return Boolean.parseBoolean(s); return d; }
    private int getListSize(Map<String, Object> p, String k) { Object v = p.get(k); return v instanceof List<?> l ? l.size() : 0; }
    @SuppressWarnings("unchecked")
    private List<?> getList(Map<String, Object> p, String k) { Object v = p.get(k); return v instanceof List<?> l ? l : null; }
}