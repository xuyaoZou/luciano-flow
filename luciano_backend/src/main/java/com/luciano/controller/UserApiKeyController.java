package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.dto.request.UserApiKeyCreateRequest;
import com.luciano.entity.UserApiKey;
import com.luciano.service.UserApiKeyService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户 API Key 管理接口
 * 双 Key 模式核心：用户自带 Key 的 CRUD 和验证
 */
@RestController
@RequestMapping("/api/v1/user/api-keys")
public class UserApiKeyController {

    private final UserApiKeyService userApiKeyService;

    public UserApiKeyController(UserApiKeyService userApiKeyService) {
        this.userApiKeyService = userApiKeyService;
    }

    /**
     * 获取当前用户的所有 API Key（脱敏）
     */
    @GetMapping
    public Result<List<UserApiKey>> list(@AuthenticationPrincipal Long userId) {
        return Result.ok(userApiKeyService.listByUserId(userId));
    }

    /**
     * 添加 API Key
     */
    @PostMapping
    public Result<UserApiKey> add(@AuthenticationPrincipal Long userId,
                                   @Valid @RequestBody UserApiKeyCreateRequest request) {
        return Result.ok(userApiKeyService.addKey(userId, request.getProviderName(),
                request.getApiKey(), request.getBaseUrl()));
    }

    /**
     * 更新 API Key
     */
    @PutMapping("/{id}")
    public Result<UserApiKey> update(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        String baseUrl = body.get("baseUrl");
        return Result.ok(userApiKeyService.updateKey(userId, id, apiKey, baseUrl));
    }

    /**
     * 删除 API Key（软删除：设为 inactive）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        userApiKeyService.deleteKey(userId, id);
        return Result.ok();
    }

    /**
     * 验证 API Key 有效性
     * TODO: 调用各厂商验证接口
     */
    @PostMapping("/{id}/verify")
    public Result<Map<String, Object>> verify(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        // 后续接入各厂商验证接口
        return Result.ok(Map.of("valid", true, "message", "验证功能待实现"));
    }
}