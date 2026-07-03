package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 用户自带 API Key（双 Key 模式）
 */
@Data
@TableName("user_api_keys")
public class UserApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String providerName;   // volcengine / siliconflow / minimax / openai / xyq
    private String encryptedKey;   // AES-256 加密存储
    private String baseUrl;        // 可选，用户自建端点
    private Boolean isActive;
    private OffsetDateTime lastVerified;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}