package com.luciano.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户 API Key 创建请求
 */
@Data
public class UserApiKeyCreateRequest {

    /**
     * 模型服务商名称（如 volcengine, siliconflow, minimax, xyq）
     */
    @NotBlank(message = "服务商名称不能为空")
    private String providerName;

    /**
     * API Key（明文，服务端加密存储）
     */
    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    /**
     * 可选：用户自建端点 URL
     */
    private String baseUrl;
}