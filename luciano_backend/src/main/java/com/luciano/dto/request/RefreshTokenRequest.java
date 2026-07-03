package com.luciano.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 token 请求
 */
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "refresh token 不能为空")
    private String refreshToken;
}