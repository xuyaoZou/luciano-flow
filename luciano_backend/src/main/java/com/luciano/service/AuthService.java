package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luciano.config.JwtUtil;
import com.luciano.dto.request.LoginRequest;
import com.luciano.dto.request.RefreshTokenRequest;
import com.luciano.dto.request.RegisterRequest;
import com.luciano.dto.response.AuthResponse;
import com.luciano.entity.User;
import com.luciano.repository.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${luciano.jwt.access-token-expiration:86400000}")
    private long accessTokenExpiration;

    /**
     * 注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 检查邮箱是否已存在
        count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );
        if (count > 0) {
            throw new IllegalArgumentException("邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("free");
        user.setCredits(0);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.insert(user);

        return generateAuthResponse(user);
    }

    /**
     * 登录
     */
    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        return generateAuthResponse(user);
    }

    /**
     * 刷新 token
     */
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("无效的 refresh token");
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new IllegalArgumentException("用户不存在或已被禁用");
        }

        // 验证数据库中存储的 refresh token 是否匹配
        if (user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)) {
            throw new IllegalArgumentException("refresh token 已失效");
        }

        return generateAuthResponse(user);
    }

    /**
     * 获取当前用户信息
     */
    public AuthResponse.UserDTO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return AuthResponse.UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .credits(user.getCredits())
                .build();
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 存储 refresh token 到数据库
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(OffsetDateTime.now().plusDays(7));
        userMapper.updateById(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpiration / 1000) // 毫秒转秒
                .user(AuthResponse.UserDTO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .avatarUrl(user.getAvatarUrl())
                        .role(user.getRole())
                        .credits(user.getCredits())
                        .build())
                .build();
    }
}