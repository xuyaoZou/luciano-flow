package com.luciano.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 步骤级模型配置创建/更新请求
 */
@Data
public class ModelConfigRequest {

    /**
     * 项目 ID（创建时必填，更新时忽略）
     */
    private Long projectId;

    /**
     * 步骤类型：character_image, scene_image, prop_image, video, tts, script_generation, first_frame, last_frame
     */
    @NotBlank(message = "步骤类型不能为空")
    private String stepType;

    /**
     * 模型服务商 ID
     */
    @NotNull(message = "服务商 ID 不能为空")
    private Long providerId;

    /**
     * 模型名称（如 stable-diffusion-xl）
     */
    private String modelName;

    /**
     * 额外配置（JSON 格式，如分辨率、参数等）
     */
    private String config;

    /**
     * 是否为该步骤的默认配置
     */
    private Boolean isDefault;

    /**
     * Key 来源：platform（平台 Key）或 user（用户自带 Key）
     */
    private String providerSource;
}