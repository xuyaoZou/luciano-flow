package com.luciano.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 认证响应：包含 access token 和 refresh token
 */
@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;     // access token 过期时间（秒）
    private UserDTO user;

    @Data
    @Builder
    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String avatarUrl;
        private String role;
        private Integer credits;
    }
}