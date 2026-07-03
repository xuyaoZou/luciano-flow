package com.luciano.adapter.kling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.adapter.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

import static com.luciano.adapter.kling.KlingConstants.*;

/**
 * 可灵 (Kling) 适配器
 * <p>
 * 支持能力：
 * - TEXT_TO_VIDEO: 文生视频（标准/Pro/Omni）
 * - IMAGE_TO_VIDEO: 图生视频
 * - FIRST_LAST_FRAME: 首尾帧生成
 * - TEXT_TO_IMAGE: 文生图
 * - CAMERA_CONTROL: 运镜控制
 * - LIP_SYNC: 对口型
 * - VIDEO_EXTEND: 视频续写
 * <p>
 * 基于 ComfyUI nodes_kling.py (3300行) + 可灵开放平台 API 文档
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KlingAdapter implements ModelAdapter {

    private final KlingApiClient apiClient;
    private final KlingSchemaProvider schemaProvider;
    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return "kling";
    }

    @Override
    public String getDisplayName() {
        return "可灵";
    }

    @Override
    public String getDescription() {
        return "快手可灵 AI 视频生成平台，支持文生视频、图生视频、首尾帧、运镜控制、对口型等";
    }

    @Override
    public Set<Capability> getCapabilities() {
        return EnumSet.of(
                Capability.TEXT_TO_VIDEO,
                Capability.IMAGE_TO_VIDEO,
                Capability.FIRST_LAST_FRAME,
                Capability.TEXT_TO_IMAGE,
                Capability.CAMERA_CONTROL,
                Capability.LIP_SYNC,
                Capability.VIDEO_EXTEND,
                Capability.OMNI_VIDEO,
                Capability.OMNI_IMAGE
        );
    }

    @Override
    public String getCostLevel() {
        return "MEDIUM";
    }

    @Override
    public CapabilitySchema getSchema(Capability capability) {
        return switch (capability) {
            case TEXT_TO_VIDEO -> schemaProvider.textToVideoSchema();
            case IMAGE_TO_VIDEO -> schemaProvider.imageToVideoSchema();
            case FIRST_LAST_FRAME -> schemaProvider.firstLastFrameSchema();
            case TEXT_TO_IMAGE -> schemaProvider.textToImageSchema();
            case CAMERA_CONTROL -> schemaProvider.cameraControlSchema();
            case LIP_SYNC -> schemaProvider.lipSyncSchema();
            case VIDEO_EXTEND -> schemaProvider.videoExtendSchema();
            case OMNI_VIDEO -> schemaProvider.omniVideoSchema();
            case OMNI_IMAGE -> schemaProvider.omniImageSchema();
            default -> null;
        };
    }

    // ==================== 参数校验 ====================

    @Override
    public ValidationResult validate(Capability capability, Map<String, Object> params) {
        if (params == null) {
            return ValidationResult.errors("参数不能为空");
        }

        List<String> errors = new ArrayList<>();

        switch (capability) {
            case TEXT_TO_VIDEO -> validateTextToVideo(params, errors);
            case IMAGE_TO_VIDEO -> validateImageToVideo(params, errors);
            case FIRST_LAST_FRAME -> validateFirstLastFrame(params, errors);
            case TEXT_TO_IMAGE -> validateTextToImage(params, errors);
            case CAMERA_CONTROL -> validateCameraControl(params, errors);
            case LIP_SYNC -> validateLipSync(params, errors);
            case VIDEO_EXTEND -> validateVideoExtend(params, errors);
            case OMNI_VIDEO -> validateOmniVideo(params, errors);
            case OMNI_IMAGE -> validateOmniImage(params, errors);
            default -> errors.add("不支持的能力: " + capability.getCode());
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.errors(errors);
    }

    private void validateTextToVideo(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) {
            errors.add("提示词不能为空");
        } else if (prompt.length() > MAX_PROMPT_T2V) {
            errors.add("提示词最长 " + MAX_PROMPT_T2V + " 字");
        }

        String modelName = getStr(params, "model_name");
        String mode = getStr(params, "mode");
        if (MODEL_KLING_V2_MASTER.equals(modelName) && !MODE_PRO.equals(mode)) {
            errors.add("kling-v2-master 仅支持 pro 模式");
        }

        String duration = getStr(params, "duration");
        if (duration != null && !DURATIONS.contains(duration)) {
            errors.add("时长只支持: " + DURATIONS);
        }

        String aspectRatio = getStr(params, "aspect_ratio");
        if (aspectRatio != null && !ASPECT_RATIOS.contains(aspectRatio)) {
            errors.add("画面比例只支持: " + ASPECT_RATIOS);
        }
    }

    private void validateImageToVideo(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) {
            errors.add("提示词不能为空");
        } else if (prompt.length() > MAX_PROMPT_I2V) {
            errors.add("提示词最长 " + MAX_PROMPT_I2V + " 字");
        }

        if (!params.containsKey("image") && !params.containsKey("image_url")) {
            errors.add("起始帧图片不能为空");
        }
    }

    private void validateFirstLastFrame(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt != null && prompt.length() > MAX_PROMPT_I2V) {
            errors.add("提示词最长 " + MAX_PROMPT_I2V + " 字");
        }

        if (!params.containsKey("first_frame") && !params.containsKey("first_frame_url")) {
            errors.add("首帧图片不能为空");
        }

        // 首尾帧模式仅支持 pro
        String mode = getStr(params, "mode");
        if (mode != null && !MODE_PRO.equals(mode)) {
            errors.add("首尾帧生成仅支持 pro 模式");
        }
    }

    private void validateTextToImage(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) {
            errors.add("提示词不能为空");
        } else if (prompt.length() > MAX_PROMPT_IMAGE) {
            errors.add("提示词最长 " + MAX_PROMPT_IMAGE + " 字");
        }

        String modelName = getStr(params, "model_name");
        String seriesAmount = getStr(params, "series_amount");
        if (MODEL_KLING_IMAGE_O1.equals(modelName) && seriesAmount != null && !"1".equals(seriesAmount)) {
            errors.add("kling-image-o1 不支持系列生成");
        }
    }

    private void validateCameraControl(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) {
            errors.add("提示词不能为空");
        }

        String cameraType = getStr(params, "camera_type", CAMERA_TYPE_SIMPLE);

        if (CAMERA_TYPE_SIMPLE.equals(cameraType)) {
            // simple 类型：6选1，至少有一个运镜参数不为 0
            // 优先检查 camera_axis + camera_value
            String axis = getStr(params, "camera_axis", null);
            Double axisValue = getDoubleObj(params, "camera_value");
            if (axis != null && axisValue != null && axisValue != 0.0) {
                // 新方式有效
            } else {
                // 兼容旧方式：检查6个独立字段
                double horizontal = getDouble(params, "horizontal", 0.0);
                double vertical = getDouble(params, "vertical", 0.0);
                double pan = getDouble(params, "pan", 0.0);
                double tilt = getDouble(params, "tilt", 0.0);
                double roll = getDouble(params, "roll", 0.0);
                double zoom = getDouble(params, "zoom", 0.0);

                if (horizontal == 0.0 && vertical == 0.0 && pan == 0.0 &&
                        tilt == 0.0 && roll == 0.0 && zoom == 0.0) {
                    errors.add("simple 运镜类型需指定运镜轴向和值（camera_axis + camera_value，或6个轴参数之一）");
                }
            }
        }
        // 预设类型（forward_up 等）无需额外参数
    }

    private void validateLipSync(Map<String, Object> params, List<String> errors) {
        if (!params.containsKey("video_id") && !params.containsKey("video_url")) {
            errors.add("输入视频不能为空（video_id 或 video_url）");
        }

        String text = getStr(params, "text");
        String audio = getStr(params, "audio_url");
        if (text == null && audio == null) {
            errors.add("文本内容和音频文件至少提供一个");
        }
        if (text != null && audio != null) {
            errors.add("文本和音频不能同时使用，请选择其一");
        }
    }

    private void validateVideoExtend(Map<String, Object> params, List<String> errors) {
        if (!params.containsKey("video_id") && !params.containsKey("video_url")) {
            errors.add("原始视频ID不能为空（video_id 或 video_url）");
        }

        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) {
            errors.add("续写提示词不能为空");
        }
    }

    private void validateOmniVideo(Map<String, Object> params, List<String> errors) {
        // Omni Video 的 prompt 在 multi_shot + shot_type=customize 时非必须
        // 但单镜头模式下应该有 prompt
        String modelName = getStr(params, "model_name", MODEL_KLING_VIDEO_O1);
        if (!MODEL_KLING_VIDEO_O1.equals(modelName) && !MODEL_KLING_V3_OMNI.equals(modelName)) {
            errors.add("Omni Video 只支持模型: " + MODEL_KLING_VIDEO_O1 + ", " + MODEL_KLING_V3_OMNI);
        }

        String mode = getStr(params, "mode", MODE_STD);
        if (!OMNI_VIDEO_MODES.contains(mode)) {
            errors.add("Omni Video mode 只支持: " + OMNI_VIDEO_MODES);
        }

        String duration = getStr(params, "duration", "5");
        if (!OMNI_VIDEO_DURATIONS.contains(duration)) {
            errors.add("Omni Video duration 只支持: " + OMNI_VIDEO_DURATIONS);
        }
    }

    private void validateOmniImage(Map<String, Object> params, List<String> errors) {
        String prompt = getStr(params, "prompt");
        if (prompt == null || prompt.isBlank()) {
            errors.add("提示词不能为空");
        }

        String modelName = getStr(params, "model_name", MODEL_KLING_IMAGE_O1);
        if (!MODEL_KLING_IMAGE_O1.equals(modelName) && !MODEL_KLING_V3_OMNI.equals(modelName)) {
            errors.add("Omni Image 只支持模型: " + MODEL_KLING_IMAGE_O1 + ", " + MODEL_KLING_V3_OMNI);
        }
    }

    // ==================== 提交任务 ====================

    @Override
    public TaskHandle submit(Capability capability, Map<String, Object> params) {
        return switch (capability) {
            case TEXT_TO_VIDEO -> submitTextToVideo(params);
            case IMAGE_TO_VIDEO -> submitImageToVideo(params);
            case FIRST_LAST_FRAME -> submitFirstLastFrame(params);
            case TEXT_TO_IMAGE -> submitTextToImage(params);
            case CAMERA_CONTROL -> submitCameraControl(params);
            case LIP_SYNC -> submitLipSync(params);
            case VIDEO_EXTEND -> submitVideoExtend(params);
            case OMNI_VIDEO -> submitOmniVideo(params);
            case OMNI_IMAGE -> submitOmniImage(params);
            default -> throw new UnsupportedOperationException("Kling 不支持能力: " + capability.getCode());
        };
    }

    private TaskHandle submitTextToVideo(Map<String, Object> params) {
        String mode = getStr(params, "mode", MODE_STD);
        String modelName = getStr(params, "model_name", MODEL_KLING_V1_6);
        String duration = getStr(params, "duration", "5");
        String aspectRatio = getStr(params, "aspect_ratio", "16:9");
        Double cfgScale = getDoubleObj(params, "cfg_scale");
        Long seed = getLong(params, "seed");

        // v2-5-turbo 在 std 模式下自动升级为 pro
        if (MODE_STD.equals(mode) && MODEL_KLING_V2_5_TURBO.equals(modelName)) {
            mode = MODE_PRO;
            log.info("[KlingAdapter] v2-5-turbo auto-upgraded to pro mode");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", getStr(params, "prompt"));
        if (params.containsKey("negative_prompt")) {
            body.put("negative_prompt", params.get("negative_prompt"));
        }
        body.put("mode", mode);
        body.put("model_name", modelName);
        body.put("duration", duration);
        body.put("aspect_ratio", aspectRatio);
        if (cfgScale != null) body.put("cfg_scale", cfgScale);
        if (seed != null && seed != 0) body.put("seed", seed);

        // 运镜控制
        if (params.containsKey("camera_control")) {
            body.put("camera_control", params.get("camera_control"));
        }

        JsonNode response = apiClient.post(PATH_TEXT_TO_VIDEO, body, JsonNode.class);
        log.info("[KlingAdapter] T2V submit response: {}", response);
        String taskId = extractTaskId(response);
        String platformTaskId = "kling:" + taskId;

        return TaskHandle.builder()
                .taskId(platformTaskId)
                .adapterId(getId())
                .capability(Capability.TEXT_TO_VIDEO)
                .providerTaskId(taskId)
                .pollPath(PATH_TEXT_TO_VIDEO)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_T2V)))
                .build();
    }

    private TaskHandle submitImageToVideo(Map<String, Object> params) {
        String mode = getStr(params, "mode", MODE_STD);
        String modelName = getStr(params, "model_name", MODEL_KLING_V1_6);
        String duration = getStr(params, "duration", "5");
        String aspectRatio = getStr(params, "aspect_ratio", "16:9");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", getStr(params, "prompt"));
        if (params.containsKey("negative_prompt")) {
            body.put("negative_prompt", params.get("negative_prompt"));
        }
        body.put("mode", mode);
        body.put("model_name", modelName);
        body.put("duration", duration);
        body.put("aspect_ratio", aspectRatio);

        // 图片（支持 image 和 image_url 两种参数名）
        if (params.containsKey("image")) {
            body.put("image", params.get("image"));
        } else if (params.containsKey("image_url")) {
            body.put("image", params.get("image_url"));
        }
        // 尾帧图片
        if (params.containsKey("image_tail")) {
            body.put("image_tail", params.get("image_tail"));
        } else if (params.containsKey("image_tail_url")) {
            body.put("image_tail", params.get("image_tail_url"));
        }

        Double cfgScale = getDoubleObj(params, "cfg_scale");
        if (cfgScale != null) body.put("cfg_scale", cfgScale);
        Long seed = getLong(params, "seed");
        if (seed != null && seed != 0) body.put("seed", seed);

        JsonNode response = apiClient.post(PATH_IMAGE_TO_VIDEO, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("kling:" + taskId)
                .adapterId(getId())
                .capability(Capability.IMAGE_TO_VIDEO)
                .providerTaskId(taskId)
                .pollPath(PATH_IMAGE_TO_VIDEO)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_I2V)))
                .build();
    }

    private TaskHandle submitFirstLastFrame(Map<String, Object> params) {
        String modelName = getStr(params, "model_name", MODEL_KLING_V1_6);
        String duration = getStr(params, "duration", "5");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", getStr(params, "prompt", ""));
        body.put("mode", MODE_PRO); // 首尾帧仅支持 pro
        body.put("model_name", modelName);
        body.put("duration", duration);

        // 首帧图片（支持 first_frame 和 first_frame_url）
        if (params.containsKey("first_frame")) {
            body.put("image", params.get("first_frame"));
        } else if (params.containsKey("first_frame_url")) {
            body.put("image", params.get("first_frame_url"));
        }
        // 尾帧图片（支持 last_frame 和 last_frame_url）
        if (params.containsKey("last_frame")) {
            body.put("image_tail", params.get("last_frame"));
        } else if (params.containsKey("last_frame_url")) {
            body.put("image_tail", params.get("last_frame_url"));
        }

        Double cfgScale = getDoubleObj(params, "cfg_scale");
        if (cfgScale != null) body.put("cfg_scale", cfgScale);
        Long seed = getLong(params, "seed");
        if (seed != null && seed != 0) body.put("seed", seed);

        JsonNode response = apiClient.post(PATH_IMAGE_TO_VIDEO, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("kling:" + taskId)
                .adapterId(getId())
                .capability(Capability.FIRST_LAST_FRAME)
                .providerTaskId(taskId)
                .pollPath(PATH_IMAGE_TO_VIDEO)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_FLF)))
                .build();
    }

    private TaskHandle submitTextToImage(Map<String, Object> params) {
        String modelName = getStr(params, "model_name", MODEL_KLING_V3_OMNI);
        String resolution = getStr(params, "resolution", "1K");
        String aspectRatio = getStr(params, "aspect_ratio", "16:9");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_name", modelName);
        body.put("prompt", getStr(params, "prompt"));
        body.put("resolution", resolution.toLowerCase());
        body.put("aspect_ratio", aspectRatio);

        String seriesAmount = getStr(params, "series_amount");
        if (seriesAmount != null && !"1".equals(seriesAmount)) {
            body.put("result_type", "series");
            body.put("series_amount", Integer.parseInt(seriesAmount));
        }

        Long seed = getLong(params, "seed");
        if (seed != null && seed != 0) body.put("seed", seed);

        String path = MODEL_KLING_V3_OMNI.equals(modelName) || MODEL_KLING_IMAGE_O1.equals(modelName)
                ? PATH_OMNI_IMAGE : PATH_IMAGE_GENERATIONS;

        JsonNode response = apiClient.post(path, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("kling:" + taskId)
                .adapterId(getId())
                .capability(Capability.TEXT_TO_IMAGE)
                .providerTaskId(taskId)
                .pollPath(path)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_IMAGE)))
                .build();
    }

    private TaskHandle submitCameraControl(Map<String, Object> params) {
        // 运镜控制通过 T2V 的 camera_control 参数实现
        // Kling API 规则：
        //   - simple 类型：config 6选1（只能有一个参数不为0）
        //   - 预设类型（forward_up/down_back/right_turn_forward/left_turn_forward）：无需 config
        Map<String, Object> enhancedParams = new HashMap<>(params);
        String cameraType = getStr(params, "camera_type", CAMERA_TYPE_SIMPLE);

        Map<String, Object> cameraControl = new LinkedHashMap<>();
        cameraControl.put("type", cameraType);

        if (CAMERA_TYPE_SIMPLE.equals(cameraType)) {
            // simple 类型：6选1，只传非零的那个参数
            // 优先使用 camera_axis + camera_value 组合，兼容旧的 6 个独立字段
            String axis = getStr(params, "camera_axis", null);
            Double axisValue = getDoubleObj(params, "camera_value");

            Map<String, Object> config = new LinkedHashMap<>();
            if (axis != null && axisValue != null) {
                // 新方式：camera_axis + camera_value
                config.put(axis, axisValue);
            } else {
                // 兼容旧方式：从6个字段中取第一个非零值
                String[] axisNames = {"horizontal", "vertical", "pan", "tilt", "roll", "zoom"};
                for (String a : axisNames) {
                    Double v = getDoubleObj(params, a);
                    if (v != null && v != 0.0) {
                        config.put(a, v);
                        break; // 6选1，只取第一个非零值
                    }
                }
            }

            if (config.isEmpty()) {
                // 没有指定运镜值，默认 zoom=5.0
                config.put("zoom", 5.0);
            }
            cameraControl.put("config", config);
        }
        // 预设类型不需要 config

        enhancedParams.put("camera_control", cameraControl);
        enhancedParams.put("model_name", MODEL_KLING_V1); // 运镜控制使用 v1
        enhancedParams.put("mode", MODE_STD);

        return submitTextToVideo(enhancedParams);
    }

    /**
     * 对口型 (Lip Sync)
     * <p>
     * 可灵对口型 API 要求参数包裹在 input 对象中：
     * - input.video_url: 视频URL（必填）
     * - input.mode: "text2video" 或 "audio2video"（必填）
     * - input.text: 文本内容（text2video 模式必填，最长120字符）
     * - input.voice_id: 音色ID（text2video 模式必填）
     * - input.voice_language: 语言 "zh"/"en"（默认 "en"）
     * - input.voice_speed: 语速 0.8~2.0（默认1.0）
     * - input.audio_url: 音频URL（audio2video 模式必填）
     * - input.audio_type: 音频类型 "url"（audio2video 时）
     * <p>
     * 注意：视频必须包含清晰人脸，否则返回 "The model did not detect a human"
     */
    private TaskHandle submitLipSync(Map<String, Object> params) {
        Map<String, Object> input = new LinkedHashMap<>();

        // mode 必填：text2video 或 audio2video
        String mode = getStr(params, "mode", "text2video");
        input.put("mode", mode);

        // 视频：优先用 video_id（可灵生成的视频ID），其次用 video_url
        if (params.containsKey("video_id")) {
            input.put("video_id", params.get("video_id"));
        } else if (params.containsKey("video_url")) {
            input.put("video_url", params.get("video_url"));
        }

        // text2video 模式参数
        if ("text2video".equals(mode)) {
            if (params.containsKey("text")) {
                input.put("text", params.get("text"));
            }
            if (params.containsKey("voice_id")) {
                input.put("voice_id", params.get("voice_id"));
            }
            input.put("voice_language", getStr(params, "voice_language", "zh"));
            input.put("voice_speed", getDouble(params, "voice_speed", 1.0));
        }

        // audio2video 模式参数
        if ("audio2video".equals(mode)) {
            if (params.containsKey("audio_url")) {
                input.put("audio_url", params.get("audio_url"));
                input.put("audio_type", "url");
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);

        JsonNode response = apiClient.post(PATH_LIP_SYNC, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("kling:" + taskId)
                .adapterId(getId())
                .capability(Capability.LIP_SYNC)
                .providerTaskId(taskId)
                .pollPath(PATH_LIP_SYNC)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_LIP_SYNC)))
                .build();
    }

    /**
     * 视频续写 (Video Extend)
     * <p>
     * 可灵续写 API 要求：
     * - video_id: 可灵生成的视频ID（必填，不支持 video_url）
     * - model_name: 模型名（默认 kling-v1-6）
     * - prompt: 续写提示词
     * - duration: 续写时长 "5" 或 "10"
     * - mode: "std" 或 "pro"
     * - negative_prompt: 反向提示词
     * - cfg_scale: 创意度
     * <p>
     * 注意：仅支持 pro 模式生成的视频续写，std 模式视频返回 "This video not supported extend-video"
     */
    private TaskHandle submitVideoExtend(Map<String, Object> params) {
        String modelName = getStr(params, "model_name", MODEL_KLING_V1_6);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_name", modelName);
        if (params.containsKey("video_id")) {
            body.put("video_id", params.get("video_id"));
        }
        if (params.containsKey("prompt")) {
            body.put("prompt", params.get("prompt"));
        }
        if (params.containsKey("negative_prompt")) {
            body.put("negative_prompt", params.get("negative_prompt"));
        }
        if (params.containsKey("duration")) {
            body.put("duration", params.get("duration"));
        }
        if (params.containsKey("mode")) {
            body.put("mode", params.get("mode"));
        }
        if (params.containsKey("cfg_scale")) {
            body.put("cfg_scale", params.get("cfg_scale"));
        }

        JsonNode response = apiClient.post(PATH_VIDEO_EXTEND, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("kling:" + taskId)
                .adapterId(getId())
                .capability(Capability.VIDEO_EXTEND)
                .providerTaskId(taskId)
                .pollPath(PATH_VIDEO_EXTEND)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_EXTEND)))
                .build();
    }

    /**
     * Omni Video（视频Omni多模态）
     * <p>
     * 支持模型：kling-video-o1、kling-v3-omni
     * 端点：/v1/videos/omni-video（注意：不是 /v1/videos/text2video）
     * <p>
     * 核心能力：多图参考、主体参考、视频编辑、多镜头分镜、首尾帧、4K、配音
     */
    private TaskHandle submitOmniVideo(Map<String, Object> params) {
        String modelName = getStr(params, "model_name", MODEL_KLING_VIDEO_O1);
        String mode = getStr(params, "mode", MODE_STD);
        String duration = getStr(params, "duration", "5");
        String aspectRatio = getStr(params, "aspect_ratio", "16:9");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_name", modelName);

        // prompt 在 multi_shot=true 且 shot_type=customize 时非必须
        if (params.containsKey("prompt")) {
            body.put("prompt", params.get("prompt"));
        }

        body.put("mode", mode);
        body.put("duration", duration);
        body.put("aspect_ratio", aspectRatio);

        // 多镜头
        if (params.containsKey("multi_shot")) {
            body.put("multi_shot", params.get("multi_shot"));
        }
        if (params.containsKey("shot_type")) {
            body.put("shot_type", params.get("shot_type"));
        }
        if (params.containsKey("multi_prompt")) {
            body.put("multi_prompt", params.get("multi_prompt"));
        }

        // 参考图（含首尾帧）— Omni Video API 格式: [{"image_url": "url", "type": "first_frame/end_frame"}]
        if (params.containsKey("image_list")) {
            Object imageList = params.get("image_list");
            if (imageList instanceof List) {
                List<Object> converted = new ArrayList<>();
                for (Object item : (List<?>) imageList) {
                    if (item instanceof String) {
                        Map<String, Object> imgObj = new LinkedHashMap<>();
                        imgObj.put("image_url", item);
                        converted.add(imgObj);
                    } else if (item instanceof Map) {
                        converted.add(item); // 已经是对象格式
                    } else {
                        converted.add(item);
                    }
                }
                body.put("image_list", converted);
            } else {
                body.put("image_list", imageList);
            }
        }

        // 主体参考 — 转换为 Kling API 格式 [{"element_id": "xxx"}]
        if (params.containsKey("element_list")) {
            Object elementList = params.get("element_list");
            if (elementList instanceof List) {
                List<Object> converted = new ArrayList<>();
                for (Object item : (List<?>) elementList) {
                    if (item instanceof String) {
                        Map<String, Object> elRef = new LinkedHashMap<>();
                        elRef.put("element_id", item);
                        converted.add(elRef);
                    } else if (item instanceof Map) {
                        converted.add(item);
                    } else {
                        converted.add(item);
                    }
                }
                body.put("element_list", converted);
            } else {
                body.put("element_list", elementList);
            }
        }

        // 参考视频
        if (params.containsKey("video_list")) {
            body.put("video_list", params.get("video_list"));
        }

        // 配音
        if (params.containsKey("sound")) {
            body.put("sound", params.get("sound"));
        }

        // 保留原声
        if (params.containsKey("keep_original_sound")) {
            body.put("keep_original_sound", params.get("keep_original_sound"));
        }

        // 其他参数
        Double cfgScale = getDoubleObj(params, "cfg_scale");
        if (cfgScale != null) body.put("cfg_scale", cfgScale);
        Long seed = getLong(params, "seed");
        if (seed != null && seed != 0) body.put("seed", seed);
        if (params.containsKey("negative_prompt")) {
            body.put("negative_prompt", params.get("negative_prompt"));
        }
        if (params.containsKey("callback_url")) {
            body.put("callback_url", params.get("callback_url"));
        }
        if (params.containsKey("external_task_id")) {
            body.put("external_task_id", params.get("external_task_id"));
        }

        JsonNode response = apiClient.post(PATH_OMNI_VIDEO, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("kling:" + taskId)
                .adapterId(getId())
                .capability(Capability.OMNI_VIDEO)
                .providerTaskId(taskId)
                .pollPath(PATH_OMNI_VIDEO)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_OMNI)))
                .build();
    }

    /**
     * Omni Image（图像Omni多模态）
     * <p>
     * 支持模型：kling-image-o1、kling-v3-omni
     * 端点：/v1/images/omni-image（注意：不是 /v1/images/generations）
     * <p>
     * 核心能力：多图参考、主体参考、组图模式
     */
    private TaskHandle submitOmniImage(Map<String, Object> params) {
        String modelName = getStr(params, "model_name", MODEL_KLING_IMAGE_O1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_name", modelName);
        body.put("prompt", getStr(params, "prompt"));

        // 分辨率
        String resolution = getStr(params, "resolution", "1k");
        body.put("resolution", resolution);

        // 画面比例
        String aspectRatio = getStr(params, "aspect_ratio", "16:9");
        body.put("aspect_ratio", aspectRatio);

        // 生成数量
        if (params.containsKey("n")) {
            body.put("n", params.get("n"));
        }

        // 组图模式
        String resultType = getStr(params, "result_type");
        if (resultType != null) {
            body.put("result_type", resultType);
            if (params.containsKey("series_amount")) {
                body.put("series_amount", params.get("series_amount"));
            }
        }

        // 参考图 — Omni Image API 格式: [{"image": "url"}]
        if (params.containsKey("image_list")) {
            Object imageList = params.get("image_list");
            if (imageList instanceof List) {
                List<Object> converted = new ArrayList<>();
                for (Object item : (List<?>) imageList) {
                    if (item instanceof String) {
                        Map<String, Object> imgObj = new LinkedHashMap<>();
                        imgObj.put("image", item);
                        converted.add(imgObj);
                    } else if (item instanceof Map) {
                        converted.add(item); // 已经是对象格式
                    } else {
                        converted.add(item);
                    }
                }
                body.put("image_list", converted);
            } else {
                body.put("image_list", imageList);
            }
        }

        // 主体参考 — 转换为 Kling API 格式 [{"element_id": "xxx"}]
        if (params.containsKey("element_list")) {
            Object elementList = params.get("element_list");
            if (elementList instanceof List) {
                List<Object> converted = new ArrayList<>();
                for (Object item : (List<?>) elementList) {
                    if (item instanceof String) {
                        Map<String, Object> elRef = new LinkedHashMap<>();
                        elRef.put("element_id", item);
                        converted.add(elRef);
                    } else if (item instanceof Map) {
                        converted.add(item);
                    } else {
                        converted.add(item);
                    }
                }
                body.put("element_list", converted);
            } else {
                body.put("element_list", elementList);
            }
        }

        // 其他参数
        Long seed = getLong(params, "seed");
        if (seed != null && seed != 0) body.put("seed", seed);
        if (params.containsKey("callback_url")) {
            body.put("callback_url", params.get("callback_url"));
        }
        if (params.containsKey("external_task_id")) {
            body.put("external_task_id", params.get("external_task_id"));
        }

        log.info("[submitOmniImage] request body = {}", body);

        JsonNode response = apiClient.post(PATH_OMNI_IMAGE, body, JsonNode.class);
        String taskId = extractTaskId(response);

        return TaskHandle.builder()
                .taskId("kling:" + taskId)
                .adapterId(getId())
                .capability(Capability.OMNI_IMAGE)
                .providerTaskId(taskId)
                .pollPath(PATH_OMNI_IMAGE)
                .createdAt(OffsetDateTime.now())
                .estimatedCompletedAt(OffsetDateTime.now().plus(java.time.Duration.ofMillis(KlingConstants.AVG_DURATION_IMAGE)))
                .build();
    }

    // ==================== 轮询 ====================

    @Override
    public TaskStatus poll(TaskHandle handle) {
        String providerTaskId = handle.getProviderTaskId();
        // 优先用提交时记录的 pollPath，避免根据 capability 推断
        String path = handle.getPollPath() != null
                ? handle.getPollPath() + "/" + providerTaskId
                : determinePollPath(handle.getCapability(), providerTaskId);

        try {
            JsonNode response = apiClient.get(path, JsonNode.class);
            String status = extractTaskStatus(response);

            // 回填平台原始状态，供操作日志使用
            handle.setPlatformStatus(status);

            return switch (status) {
                case TASK_STATUS_SUCCEED -> TaskStatus.COMPLETED;
                case TASK_STATUS_FAILED -> TaskStatus.FAILED;
                case TASK_STATUS_PROCESSING -> TaskStatus.PROCESSING;
                default -> TaskStatus.PENDING;
            };
        } catch (Exception e) {
            log.error("[KlingAdapter] Poll failed for task {}: {}", providerTaskId, e.getMessage());
            return TaskStatus.PROCESSING; // 轮询失败继续等
        }
    }

    @Override
    public MediaResult download(TaskHandle handle) {
        String providerTaskId = handle.getProviderTaskId();
        String path = handle.getPollPath() != null
                ? handle.getPollPath() + "/" + providerTaskId
                : determinePollPath(handle.getCapability(), providerTaskId);

        try {
            JsonNode response = apiClient.get(path, JsonNode.class);
            List<String> urls = extractResultUrls(response, handle.getCapability());

            if (urls.isEmpty()) {
                throw new RuntimeException("Kling 任务结果为空: " + providerTaskId);
            }

            String mediaType = handle.getCapability().getCategory().equals("视频") ? "video" : "image";

            // 主结果 + 额外结果
            MediaResult.MediaResultBuilder builder = MediaResult.builder()
                    .mediaType(mediaType)
                    .originalUrl(urls.get(0));

            if (urls.size() > 1) {
                builder.additionalUrls(urls.subList(1, urls.size()));
            }

            return builder.build();
        } catch (Exception e) {
            log.error("[KlingAdapter] Download failed for task {}: {}", providerTaskId, e.getMessage());
            throw new RuntimeException("Kling 下载结果失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancel(TaskHandle handle) {
        // Kling API 暂不支持取消任务
        log.warn("[KlingAdapter] Cancel not supported for task: {}", handle.getTaskId());
    }

    // ==================== 费用预估 ====================

    @Override
    public CostEstimate estimateCost(Capability capability, Map<String, Object> params) {
        return switch (capability) {
            case TEXT_TO_VIDEO -> estimateT2VCost(params);
            case IMAGE_TO_VIDEO -> estimateI2VCost(params);
            case FIRST_LAST_FRAME -> CostEstimate.builder()
                    .amount(java.math.BigDecimal.valueOf(COST_I2V_PRO_5S))
                    .currency("USD")
                    .billingModel("按次计费")
                    .displayText("约 $0.49~0.98/次")
                    .estimatedDurationMs(AVG_DURATION_FLF)
                    .build();
            case TEXT_TO_IMAGE -> estimateImageCost(params);
            case CAMERA_CONTROL -> CostEstimate.builder()
                    .amount(java.math.BigDecimal.valueOf(COST_T2V_STD_5S))
                    .currency("USD")
                    .billingModel("按次计费")
                    .displayText("约 $0.14/次")
                    .estimatedDurationMs(AVG_DURATION_T2V)
                    .build();
            case LIP_SYNC -> CostEstimate.builder()
                    .amount(java.math.BigDecimal.valueOf(COST_LIP_SYNC))
                    .currency("USD")
                    .billingModel("按次计费")
                    .displayText("约 $0.49/次")
                    .estimatedDurationMs(AVG_DURATION_LIP_SYNC)
                    .build();
            case VIDEO_EXTEND -> CostEstimate.builder()
                    .amount(java.math.BigDecimal.valueOf(COST_T2V_STD_5S))
                    .currency("USD")
                    .billingModel("按次计费")
                    .displayText("约 $0.14~0.98/次")
                    .estimatedDurationMs(AVG_DURATION_EXTEND)
                    .build();
            case OMNI_VIDEO -> estimateOmniVideoCost(params);
            case OMNI_IMAGE -> estimateOmniImageCost(params);
            default -> CostEstimate.builder()
                    .currency("USD").billingModel("未知")
                    .displayText("费用未知").build();
        };
    }

    private CostEstimate estimateT2VCost(Map<String, Object> params) {
        String mode = getStr(params, "mode", MODE_STD);
        String modelName = getStr(params, "model_name", MODEL_KLING_V1_6);
        String duration = getStr(params, "duration", "5");

        double cost;
        if (MODEL_KLING_V2_MASTER.equals(modelName) || MODEL_KLING_V2_1_MASTER.equals(modelName)) {
            cost = "10".equals(duration) ? COST_T2V_V2M_10S : COST_T2V_V2M_5S;
        } else if (MODEL_KLING_V2_5_TURBO.equals(modelName)) {
            cost = "10".equals(duration) ? COST_T2V_V25T_10S : COST_T2V_V25T_5S;
        } else if (MODE_PRO.equals(mode)) {
            cost = "10".equals(duration) ? COST_T2V_PRO_10S : COST_T2V_PRO_5S;
        } else {
            cost = "10".equals(duration) ? COST_T2V_STD_10S : COST_T2V_STD_5S;
        }

        return CostEstimate.builder()
                .amount(java.math.BigDecimal.valueOf(cost))
                .currency("USD")
                .billingModel("按次计费")
                .displayText(String.format("约 $%.2f/次", cost))
                .estimatedDurationMs(AVG_DURATION_T2V)
                .build();
    }

    private CostEstimate estimateI2VCost(Map<String, Object> params) {
        String mode = getStr(params, "mode", MODE_STD);
        double cost = MODE_PRO.equals(mode) ? COST_I2V_PRO_5S : COST_I2V_STD_5S;

        return CostEstimate.builder()
                .amount(java.math.BigDecimal.valueOf(cost))
                .currency("USD")
                .billingModel("按次计费")
                .displayText(String.format("约 $%.2f/次", cost))
                .estimatedDurationMs(AVG_DURATION_I2V)
                .build();
    }

    private CostEstimate estimateImageCost(Map<String, Object> params) {
        String resolution = getStr(params, "resolution", "1K");
        double costPerImage = switch (resolution.toUpperCase()) {
            case "4K" -> COST_IMAGE_4K;
            case "2K" -> COST_IMAGE_2K;
            default -> COST_IMAGE_1K;
        };

        String seriesAmount = getStr(params, "series_amount", "1");
        int count = "disabled".equals(seriesAmount) ? 1 : Integer.parseInt(seriesAmount);
        double totalCost = costPerImage * count;

        return CostEstimate.builder()
                .amount(java.math.BigDecimal.valueOf(totalCost))
                .currency("USD")
                .billingModel("按次计费")
                .displayText(String.format("约 $%.3f/次", totalCost))
                .estimatedDurationMs(AVG_DURATION_IMAGE)
                .build();
    }

    private CostEstimate estimateOmniVideoCost(Map<String, Object> params) {
        String mode = getStr(params, "mode", MODE_STD);
        String durationStr = getStr(params, "duration", "5");
        int duration = Integer.parseInt(durationStr);

        double costPerSec = switch (mode) {
            case "4k" -> COST_OMNI_4K_PER_SEC;
            case MODE_PRO -> COST_OMNI_PRO_PER_SEC;
            default -> COST_OMNI_STD_PER_SEC;
        };
        double totalCost = costPerSec * duration;

        return CostEstimate.builder()
                .amount(java.math.BigDecimal.valueOf(totalCost))
                .currency("USD")
                .billingModel("按秒计费")
                .displayText(String.format("约 $%.2f (%ds %s)", totalCost, duration, mode))
                .estimatedDurationMs(AVG_DURATION_OMNI)
                .build();
    }

    private CostEstimate estimateOmniImageCost(Map<String, Object> params) {
        String resolution = getStr(params, "resolution", "1k");
        double costPerImage = switch (resolution.toLowerCase()) {
            case "4k" -> COST_IMAGE_4K;
            case "2k" -> COST_IMAGE_2K;
            default -> COST_IMAGE_1K;
        };

        int n = params.containsKey("n") ? ((Number) params.get("n")).intValue() : 1;
        double totalCost = costPerImage * n;

        return CostEstimate.builder()
                .amount(java.math.BigDecimal.valueOf(totalCost))
                .currency("USD")
                .billingModel("按次计费")
                .displayText(String.format("约 $%.3f/次", totalCost))
                .estimatedDurationMs(AVG_DURATION_IMAGE)
                .build();
    }

    // ==================== 私有辅助 ====================

    private String determinePollPath(Capability capability, String providerTaskId) {
        // Kling API 轮询路径必须带子路径，/v1/videos/{id} 会 404
        if (capability == null) {
            // 兜底：尝试通用路径（可能 404，但不抛异常）
            log.warn("[KlingAdapter] No capability info for task {}, using fallback /v1/videos/ path", providerTaskId);
            return "/v1/videos/" + providerTaskId;
        }
        return switch (capability) {
            case TEXT_TO_VIDEO -> PATH_TEXT_TO_VIDEO + "/" + providerTaskId;
            case IMAGE_TO_VIDEO -> PATH_IMAGE_TO_VIDEO + "/" + providerTaskId;
            case FIRST_LAST_FRAME -> PATH_IMAGE_TO_VIDEO + "/" + providerTaskId;
            case CAMERA_CONTROL -> PATH_TEXT_TO_VIDEO + "/" + providerTaskId;  // 运镜控制走 T2V 端点
            case LIP_SYNC -> PATH_LIP_SYNC + "/" + providerTaskId;
            case VIDEO_EXTEND -> PATH_VIDEO_EXTEND + "/" + providerTaskId;
            case VIDEO_EDIT -> "/v1/videos/edit/" + providerTaskId;
            case MOTION_CONTROL -> "/v1/videos/motion/" + providerTaskId;
            case MULTI_CHARACTER -> "/v1/videos/multi-character/" + providerTaskId;
            case TEXT_TO_IMAGE -> PATH_IMAGE_GENERATIONS + "/" + providerTaskId;
            case OMNI_VIDEO -> PATH_OMNI_VIDEO + "/" + providerTaskId;
            case OMNI_IMAGE -> PATH_OMNI_IMAGE + "/" + providerTaskId;
            default -> "/v1/videos/" + providerTaskId;
        };
    }

    private String extractTaskId(JsonNode response) {
        if (response.has("data")) {
            JsonNode data = response.get("data");
            if (data.has("task_id")) {
                return data.get("task_id").asText();
            }
        }
        throw new RuntimeException("Kling 返回数据缺少 task_id: " + response);
    }

    private String extractTaskStatus(JsonNode response) {
        if (response.has("data")) {
            JsonNode data = response.get("data");
            if (data.has("task_status")) {
                return data.get("task_status").asText();
            }
        }
        return TASK_STATUS_SUBMITTED;
    }

    private String extractResultUrl(JsonNode response, Capability capability) {
        List<String> urls = extractResultUrls(response, capability);
        return urls.isEmpty() ? null : urls.get(0);
    }

    /**
     * 提取所有结果 URL（支持多图/组图）
     */
    private List<String> extractResultUrls(JsonNode response, Capability capability) {
        if (!response.has("data")) return java.util.Collections.emptyList();
        JsonNode data = response.get("data");
        if (!data.has("task_result")) return java.util.Collections.emptyList();
        JsonNode result = data.get("task_result");

        List<String> urls = new ArrayList<>();

        if (capability == Capability.TEXT_TO_IMAGE || capability == Capability.OMNI_IMAGE) {
            // 组图模式：series_images
            if (result.has("series_images") && result.get("series_images").isArray()) {
                for (JsonNode img : result.get("series_images")) {
                    if (img.has("url")) urls.add(img.get("url").asText());
                }
            }
            // 普通多图：images
            if (urls.isEmpty() && result.has("images") && result.get("images").isArray()) {
                for (JsonNode img : result.get("images")) {
                    if (img.has("url")) urls.add(img.get("url").asText());
                }
            }
        } else {
            // 视频
            if (result.has("videos") && result.get("videos").isArray()) {
                for (JsonNode vid : result.get("videos")) {
                    if (vid.has("url")) urls.add(vid.get("url").asText());
                }
            }
        }

        return urls;
    }

    private String getStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    private String getStr(Map<String, Object> params, String key, String defaultVal) {
        Object val = params.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private Double getDoubleObj(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof Number num) return num.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private double getDouble(Map<String, Object> params, String key, double defaultVal) {
        Double val = getDoubleObj(params, key);
        return val != null ? val : defaultVal;
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof Number num) return num.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}