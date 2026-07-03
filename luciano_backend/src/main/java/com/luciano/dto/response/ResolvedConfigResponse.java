package com.luciano.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 解析后的模型配置响应
 * 包含实际可用的 Key 和 URL，供前端展示
 */
@Data
@Builder
public class ResolvedConfigResponse {
    private String provider;
    private String providerSource;
    private String modelName;
    private String baseUrl;
    private Boolean hasUserKey;  // 用户是否配置了自己的 Key
    private String maskedApiKey; // 脱敏后的 Key（仅展示用）
}