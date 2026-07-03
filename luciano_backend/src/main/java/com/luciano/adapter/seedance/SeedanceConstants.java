package com.luciano.adapter.seedance;

import java.util.List;

/**
 * Seedance (ByteDance) API 常量定义
 * 基于 ComfyUI nodes_bytedance.py + 火山引擎 API 文档
 */
public final class SeedanceConstants {

    private SeedanceConstants() {}

    // ==================== API 域名 ====================
    public static final String BASE_URL_CN = "https://ark.cn-beijing.volces.com";
    public static final String BASE_URL_INTERNATIONAL = "https://api.byteplus.com";

    // ==================== API 路径 ====================
    // 火山方舟国内版（RESTful 风格，Bearer Token 认证）
    public static final String PATH_TASK = "/api/v3/contents/generations/tasks";
    public static final String PATH_TASK_STATUS = "/api/v3/contents/generations/tasks";  // + /{task_id}
    public static final String PATH_IMAGE = "/api/v3/images/generations";

    // 资产管理（方舟平台不使用，保留兼容）
    public static final String PATH_ASSET_CREATE = "/api/v3/assets";
    public static final String PATH_ASSET_GET = "/api/v3/assets";  // + /{asset_id}

    // ==================== 任务状态（方舟 API 实际返回值） ====================
    public static final String TASK_STATUS_QUEUED = "queued";
    public static final String TASK_STATUS_RUNNING = "running";
    public static final String TASK_STATUS_SUCCEEDED = "succeeded";
    public static final String TASK_STATUS_FAILED = "failed";
    public static final String TASK_STATUS_EXPIRED = "expired";

    // 兼容旧引用（Kling 状态值，方舟不会返回这些）
    @Deprecated public static final String TASK_STATUS_SUBMITTED = TASK_STATUS_QUEUED;
    @Deprecated public static final String TASK_STATUS_PROCESSING = TASK_STATUS_RUNNING;
    @Deprecated public static final String TASK_STATUS_SUCCEED = TASK_STATUS_SUCCEEDED;

    // ==================== Seedance 1.x 模型 ====================
    // 国内版（火山方舟）用 doubao- 前缀
    public static final String MODEL_SD15_PRO = "doubao-seedance-1-5-pro-251215";
    public static final String MODEL_SD10_PRO = "doubao-seedance-1-0-pro-250528";
    public static final String MODEL_SD10_PRO_FAST = "doubao-seedance-1-0-pro-fast-251015";
    public static final String MODEL_SD10_LITE_T2V = "doubao-seedance-1-0-lite-t2v-250428";
    public static final String MODEL_SD10_LITE_I2V = "doubao-seedance-1-0-lite-i2v-250428";

    // ==================== Seedance 2.0 模型 ====================
    // 国内版（火山方舟）用 doubao- 前缀
    // 国际版（BytePlus）用 dreamina- 前缀
    public static final String MODEL_SD20 = "doubao-seedance-2-0-260128";
    public static final String MODEL_SD20_FAST = "doubao-seedance-2-0-fast-260128";
    // 国际版备选
    public static final String MODEL_SD20_INTERNATIONAL = "dreamina-seedance-2-0-260128";
    public static final String MODEL_SD20_FAST_INTERNATIONAL = "dreamina-seedance-2-0-fast-260128";

    // ==================== Seedream 图片模型 ====================
    public static final String MODEL_SEEDREAM_5_LITE = "doubao-seedream-5-0-260128";
    public static final String MODEL_SEEDREAM_45 = "doubao-seedream-4-5-251128";
    public static final String MODEL_SEEDREAM_40 = "doubao-seedream-4-0-250828";
    public static final String MODEL_SEEDREAM_30 = "doubao-seedream-3-0-t2i-250415"; // 已废弃

    // ==================== 画面比例 ====================
    public static final List<String> ASPECT_RATIOS_V1 = List.of(
            "16:9", "4:3", "1:1", "3:4", "9:16", "21:9"
    );
    public static final List<String> ASPECT_RATIOS_V2 = List.of(
            "16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive"
    );

    // ==================== 分辨率 ====================
    public static final List<String> RESOLUTIONS_V1 = List.of("480p", "720p", "1080p");
    public static final List<String> RESOLUTIONS_SD20 = List.of("480p", "720p", "1080p");
    public static final List<String> RESOLUTIONS_SD20_FAST = List.of("480p", "720p");

    // ==================== 时长 ====================
    public static final int DURATION_MIN_V1 = 3;
    public static final int DURATION_MAX_V1 = 12;
    public static final int DURATION_MIN_V2 = 4;
    public static final int DURATION_MAX_V2 = 15;
    public static final int DURATION_DEFAULT_V2 = 7;

    // ==================== Seedance 2.0 参考限制 ====================
    public static final int MAX_REFERENCE_IMAGES = 9;
    public static final int MAX_REFERENCE_VIDEOS = 3;
    public static final int MAX_REFERENCE_AUDIOS = 3;
    public static final double MAX_VIDEO_DURATION_SEC = 15.1;
    public static final double MIN_VIDEO_DURATION_SEC = 1.8;

    // ==================== 图片尺寸限制 ====================
    public static final int IMAGE_MIN_WIDTH = 300;
    public static final int IMAGE_MIN_HEIGHT = 300;
    public static final int IMAGE_MAX_WIDTH = 6000;
    public static final int IMAGE_MAX_HEIGHT = 6000;
    public static final double IMAGE_ASPECT_RATIO_MIN = 0.4;  // 2:5
    public static final double IMAGE_ASPECT_RATIO_MAX = 2.5;  // 5:2

    // ==================== Seedream 图片分辨率限制 ====================
    public static final int SEEDREAM_MIN_PIXELS_45 = 3686400;   // 3.68MP
    public static final int SEEDREAM_MIN_PIXELS_40 = 921600;    // 0.92MP
    public static final int SEEDREAM_MAX_PIXELS_50 = 10404496; // ~10.4MP
    public static final int SEEDREAM_MAX_PIXELS_DEFAULT = 16777216; // ~16.78MP
    public static final int SEEDREAM_MAX_REF_IMAGES_50 = 14;
    public static final int SEEDREAM_MAX_REF_IMAGES_DEFAULT = 10;
    public static final int SEEDREAM_MAX_TOTAL_IMAGES = 15;

    // ==================== 轮询配置 ====================
    // Seedance 1.x 执行时间（秒，10秒视频@各分辨率）
    public static final java.util.Map<String, java.util.Map<String, Integer>> V1_EXEC_TIME = java.util.Map.of(
            MODEL_SD15_PRO, java.util.Map.of("480p", 90, "720p", 200, "1080p", 440),
            MODEL_SD10_PRO, java.util.Map.of("480p", 180, "720p", 400, "1080p", 920),
            MODEL_SD10_PRO_FAST, java.util.Map.of("480p", 70, "720p", 160, "1080p", 360),
            MODEL_SD10_LITE_T2V, java.util.Map.of("480p", 130, "720p", 290, "1080p", 660),
            MODEL_SD10_LITE_I2V, java.util.Map.of("480p", 130, "720p", 290)
    );

    public static final int POLL_INTERVAL_MS = 9000; // 9秒
    public static final int POLL_TIMEOUT_MS = 900000; // 15分钟

    // ==================== 计费（USD，按10秒视频估算） ====================
    // Seedance 1.x 按次计费
    public static final java.util.Map<String, java.util.Map<String, double[]>> V1_PRICES = java.util.Map.of(
            MODEL_SD15_PRO, java.util.Map.of("480p", new double[]{0.12}, "720p", new double[]{0.26}, "1080p", new double[]{0.58}),
            MODEL_SD10_PRO, java.util.Map.of("480p", new double[]{0.23}, "720p", new double[]{0.51}, "1080p", new double[]{1.18}),
            MODEL_SD10_PRO_FAST, java.util.Map.of("480p", new double[]{0.09}, "720p", new double[]{0.21}, "1080p", new double[]{0.47}),
            MODEL_SD10_LITE_T2V, java.util.Map.of("480p", new double[]{0.17}, "720p", new double[]{0.37}, "1080p", new double[]{0.85}),
            MODEL_SD10_LITE_I2V, java.util.Map.of("480p", new double[]{0.17}, "720p", new double[]{0.37})
    );

    // Seedance 2.0 按 token 计费
    public static final double SD20_PRICE_PER_1K_TOKENS = 0.01001;     // $/1K tokens
    public static final double SD20_FAST_PRICE_PER_1K_TOKENS = 0.008008;
    public static final double SD20_VIDEO_PRICE_PER_1K_TOKENS = 0.006149;
    public static final double SD20_FAST_VIDEO_PRICE_PER_1K_TOKENS = 0.004719;

    // Token 速率（tokens/秒，按分辨率）
    public static final java.util.Map<String, Integer> TOKEN_RATE = java.util.Map.of(
            "480p", 10044, "720p", 21600, "1080p", 48800
    );

    // Seedream 图片按次计费
    public static final double COST_SEEDREAM_30 = 0.03;
    public static final double COST_SEEDREAM_40 = 0.03;
    public static final double COST_SEEDREAM_45 = 0.04;
    public static final double COST_SEEDREAM_5_LITE = 0.035;
}