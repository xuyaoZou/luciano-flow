package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.dto.request.LoginRequest;
import com.luciano.dto.request.RefreshTokenRequest;
import com.luciano.dto.request.RegisterRequest;
import com.luciano.dto.response.AuthResponse;
import com.luciano.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 刷新 token
     */
    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.ok(authService.refresh(request));
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<AuthResponse.UserDTO> me(@AuthenticationPrincipal Long userId) {
        return Result.ok(authService.getCurrentUser(userId));
    }
}