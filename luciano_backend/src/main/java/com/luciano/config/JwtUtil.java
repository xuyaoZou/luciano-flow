package com.luciano.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成、解析、验证 token
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtUtil(
            @Value("${luciano.jwt.secret:luciano-platform-secret-key-must-be-at-least-32-chars}") String secret,
            @Value("${luciano.jwt.access-token-expiration:86400000}") long accessTokenExpiration,
            @Value("${luciano.jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;   // 默认24小时
        this.refreshTokenExpiration = refreshTokenExpiration;   // 默认7天
    }

    /**
     * 生成 access token
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * 生成 refresh token
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，返回 Claims。无效 token 返回 null。
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 从 token 中获取 userId
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 token 中获取 username
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    /**
     * 从 token 中获取 role
     */
    public String getRole(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.get("role", String.class);
    }

    /**
     * 验证 token 是否有效
     */
    public boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    /**
     * 验证是否是 access token
     */
    public boolean isAccessToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return false;
        return "access".equals(claims.get("type", String.class));
    }

    /**
     * 验证是否是 refresh token
     */
    public boolean isRefreshToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return false;
        return "refresh".equals(claims.get("type", String.class));
    }
}