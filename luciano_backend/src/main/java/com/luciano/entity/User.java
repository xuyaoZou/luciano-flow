package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String avatarUrl;
    private String role;
    private Integer credits;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "NOW()")
    private OffsetDateTime deletedAt;

    private String refreshToken;
    private OffsetDateTime refreshTokenExpiresAt;
}