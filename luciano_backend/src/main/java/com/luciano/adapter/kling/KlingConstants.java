package com.luciano.adapter.kling;

import java.util.List;

/**
 * Kling API 常量定义
 * 基于可灵开放平台 API 文档 + ComfyUI nodes_kling.py 源码
 */
public final class KlingConstants {

    private KlingConstants() {}

    // ==================== API 域名 ====================
    /** 国内版 API 域名（优先使用） */
    public static final String BASE_URL_CN = "https://api-beijing.klingai.com";
    /** 国际版 API 域名 */
    public static final String BASE_URL_GLOBAL = "https://api.klingai.com";

    // ==================== API 路径 ====================
    public static final String PATH_AUTH_TOKEN = "/v1/oauth/token";
    public static final String PATH_TEXT_TO_VIDEO = "/v1/videos/text2video";
    public static final String PATH_IMAGE_TO_VIDEO = "/v1/videos/image2video";
    public static final String PATH_OMNI_VIDEO = "/v1/videos/omni-video";
    public static final String PATH_VIDEO_EXTEND = "/v1/videos/video-extend";
    public static final String PATH_LIP_SYNC = "/v1/videos/lip-sync";
    public static final String PATH_VIDEO_EFFECTS = "/v1/videos/effects";
    public static final String PATH_IMAGE_GENERATIONS = "/v1/images/generations";
    public static final String PATH_OMNI_IMAGE = "/v1/images/omni-image";

    // ==================== 主体管理（Element） ====================
    /** 创建自定义主体（异步，返回 task_id） */
    public static final String PATH_ELEMENT_CREATE = "/v1/general/advanced-custom-elements";
    /** 查询单个主体任务状态 / 自定义主体列表 */
    public static final String PATH_ELEMENT_QUERY = "/v1/general/advanced-custom-elements";
    /** 查询官方预设主体列表 */
    public static final String PATH_ELEMENT_PRESETS = "/v1/general/advanced-presets-elements";
    /** 删除主体 */
    public static final String PATH_ELEMENT_DELETE = "/v1/general/delete-elements";

    // ==================== 任务状态 ====================
    /** 已提交 */
    public static final String TASK_STATUS_SUBMITTED = "submitted";
    /** 处理中 */
    public static final String TASK_STATUS_PROCESSING = "processing";
    /** 已完成 */
    public static final String TASK_STATUS_SUCCEED = "succeed";
    /** 失败 */
    public static final String TASK_STATUS_FAILED = "failed";

    // ==================== 模型版本 ====================
    public static final String MODEL_KLING_V1 = "kling-v1";
    public static final String MODEL_KLING_V1_5 = "kling-v1-5";
    public static final String MODEL_KLING_V1_6 = "kling-v1-6";
    public static final String MODEL_KLING_V2 = "kling-v2";
    public static final String MODEL_KLING_V2_MASTER = "kling-v2-master";
    public static final String MODEL_KLING_V2_1 = "kling-v2-1";
    public static final String MODEL_KLING_V2_1_MASTER = "kling-v2-1-master";
    public static final String MODEL_KLING_V2_5_TURBO = "kling-v2-5-turbo";
    public static final String MODEL_KLING_V3_OMNI = "kling-v3-omni";
    public static final String MODEL_KLING_VIDEO_O1 = "kling-video-o1";
    public static final String MODEL_KLING_IMAGE_O1 = "kling-image-o1";

    // ==================== 生成模式 ====================
    public static final String MODE_STD = "std";
    public static final String MODE_PRO = "pro";

    // ==================== 画面比例 ====================
    public static final List<String> ASPECT_RATIOS = List.of(
            "16:9", "9:16", "1:1"
    );

    // ==================== 视频时长 ====================
    public static final List<String> DURATIONS = List.of(
            "5", "10"
    );

    // ==================== Omni 模型时长 ====================
    public static final int OMNI_DURATION_MIN = 3;
    public static final int OMNI_DURATION_MAX = 15;

    // ==================== Omni 分辨率 ====================
    public static final List<String> OMNI_RESOLUTIONS = List.of(
            "720p", "1080p", "4k"
    );

    // ==================== 图片分辨率 ====================
    public static final List<String> IMAGE_RESOLUTIONS = List.of(
            "1K", "2K", "4K"
    );

    // ==================== 图片画面比例 ====================
    public static final List<String> IMAGE_ASPECT_RATIOS = List.of(
            "16:9", "9:16", "1:1", "4:3", "3:4", "3:2", "2:3", "21:9"
    );

    // ==================== 运镜类型 ====================
    public static final String CAMERA_TYPE_SIMPLE = "simple";
    public static final String CAMERA_TYPE_FORWARD_UP = "forward_up";
    public static final String CAMERA_TYPE_DOWN_BACK = "down_back";
    public static final String CAMERA_TYPE_RIGHT_TURN_FORWARD = "right_turn_forward";
    public static final String CAMERA_TYPE_LEFT_TURN_FORWARD = "left_turn_forward";

    // simple 类型：config 6选1，只能有一个参数不为0
    // forward_up/down_back/right_turn_forward/left_turn_forward：预设类型，无需 config

    // ==================== 对口型语言 ====================
    public static final List<String> LIP_SYNC_LANGUAGES = List.of(
            "zh", "en"
    );

    // ==================== 对口型模式 ====================
    public static final String LIP_SYNC_MODE_TEXT = "text2video";
    public static final String LIP_SYNC_MODE_AUDIO = "audio2video";

    // ==================== 对口型音色 ID ====================
    // 英文音色
    public static final List<String> VOICE_IDS_EN = List.of(
            "girlfriend_4_speech02", "genshin_vindi2", "zhinen_xuesheng",
            "AOT", "ai_shatang", "genshin_klee2", "genshin_kirara",
            "ai_kaiya", "oversea_male1", "ai_chenjiahao_712",
            "chat1_female_new-3", "chat_0407_5-1", "cartoon-boy-07",
            "cartoon-girl-01", "ai_huangzhong_712", "ai_huangyaoshi_712",
            "ai_laoguowang_712", "chengshu_jiejie", "you_pingjing",
            "laopopo_speech02"
    );
    // 中文音色
    public static final List<String> VOICE_IDS_ZH = List.of(
            "genshin_vindi2", "zhinen_xuesheng", "tiyuxi_xuedi",
            "ai_shatang", "genshin_klee2", "genshin_kirara",
            "ai_kaiya", "tiexin_nanyou", "ai_chenjiahao_712",
            "girlfriend_1_speech02", "chat1_female_new-3",
            "girlfriend_2_speech02", "cartoon-boy-07", "cartoon-girl-01",
            "ai_huangyaoshi_712", "you_pingjing", "ai_laoguowang_712",
            "chengshu_jiejie", "zhuxi_speech02", "laopopo_speech02"
    );

    // ==================== 特效场景 ====================
    // 单人特效
    public static final List<String> SINGLE_EFFECT_SCENES = List.of(
            "ghost_in_shell", "transformers", "cyberpunk", "anime_style"
    );

    // 双人特效
    public static final List<String> DUAL_EFFECT_SCENES = List.of(
            "kiss", "hug", "handshake"
    );

    // ==================== 轮询配置 ====================
    /** 平均生成耗时（毫秒） */
    public static final int AVG_DURATION_T2V = 319000;   // ~5.3分钟
    public static final int AVG_DURATION_I2V = 164000;    // ~2.7分钟
    public static final int AVG_DURATION_FLF = 164000;    // ~2.7分钟
    public static final int AVG_DURATION_OMNI = 300000;   // ~5分钟
    public static final int AVG_DURATION_LIP_SYNC = 455000; // ~7.6分钟
    public static final int AVG_DURATION_EXTEND = 320000; // ~5.3分钟
    public static final int AVG_DURATION_IMAGE = 32000;    // ~32秒

    /** 轮询间隔（毫秒） */
    public static final int POLL_INTERVAL_MS = 3000;
    /** 轮询超时（毫秒） */
    public static final int POLL_TIMEOUT_MS = 600000; // 10分钟

    // ==================== 字符限制 ====================
    public static final int MAX_PROMPT_T2V = 2500;
    public static final int MAX_PROMPT_I2V = 500;
    public static final int MAX_PROMPT_IMAGE = 500;
    public static final int MAX_NEGATIVE_PROMPT_IMAGE = 200;
    public static final int MAX_PROMPT_LIP_SYNC = 120;

    // ==================== 图片尺寸限制 ====================
    public static final int IMAGE_MIN_WIDTH = 300;
    public static final int IMAGE_MIN_HEIGHT = 300;
    public static final double IMAGE_ASPECT_RATIO_MIN = 1.0 / 2.5;
    public static final double IMAGE_ASPECT_RATIO_MAX = 2.5;

    // ==================== 计费（USD） ====================
    // T2V 标准模式
    public static final double COST_T2V_STD_5S = 0.28;
    public static final double COST_T2V_STD_10S = 0.56;
    // T2V Pro 模式
    public static final double COST_T2V_PRO_5S = 0.49;
    public static final double COST_T2V_PRO_10S = 0.98;
    // T2V v2-master
    public static final double COST_T2V_V2M_5S = 1.4;
    public static final double COST_T2V_V2M_10S = 2.8;
    // T2V v2-5-turbo
    public static final double COST_T2V_V25T_5S = 0.35;
    public static final double COST_T2V_V25T_10S = 0.7;
    // Omni Video
    public static final double COST_OMNI_STD_PER_SEC = 0.084;
    public static final double COST_OMNI_PRO_PER_SEC = 0.112;
    public static final double COST_OMNI_4K_PER_SEC = 0.42;
    // I2V
    public static final double COST_I2V_STD_5S = 0.35;
    public static final double COST_I2V_PRO_5S = 0.49;
    // Lip Sync
    public static final double COST_LIP_SYNC = 0.49;
    // Image
    public static final double COST_IMAGE_1K = 0.028;
    public static final double COST_IMAGE_2K = 0.028;
    public static final double COST_IMAGE_4K = 0.056;

    // ==================== Omni 模型常量 ====================
    /** Omni Video 模型 */
    // MODEL_KLING_VIDEO_O1, MODEL_KLING_V3_OMNI, MODEL_KLING_IMAGE_O1 已在上方模型常量区定义
    // 注意：kling-v3-omni 也可用于 Omni Image 端点

    /** Omni Video 时长范围 */
    public static final List<String> OMNI_VIDEO_DURATIONS = List.of(
            "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"
    );

    /** Omni Video 模式（含 4K） */
    public static final List<String> OMNI_VIDEO_MODES = List.of(
            MODE_STD, MODE_PRO, "4k"
    );

    /** Omni Image 分辨率 */
    public static final List<String> OMNI_IMAGE_RESOLUTIONS = List.of(
            "1k", "2k", "4k"
    );

    /** Omni Image 结果类型 */
    public static final String RESULT_TYPE_SINGLE = "single";
    public static final String RESULT_TYPE_SERIES = "series";

    /** Omni Video 参考类型 */
    public static final String VIDEO_REFER_TYPE_FEATURE = "feature";
    public static final String VIDEO_REFER_TYPE_BASE = "base";

    /** Omni Video 帧类型 */
    public static final String IMAGE_TYPE_FIRST_FRAME = "first_frame";
    public static final String IMAGE_TYPE_END_FRAME = "end_frame";

    /** Omni Video 声音开关 */
    public static final String SOUND_ON = "on";
    public static final String SOUND_OFF = "off";

    /** Omni 多镜头分镜类型 */
    public static final String SHOT_TYPE_CUSTOMIZE = "customize";
    public static final String SHOT_TYPE_INTELLIGENCE = "intelligence";
}