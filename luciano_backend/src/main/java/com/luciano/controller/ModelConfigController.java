package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.dto.request.ModelConfigRequest;
import com.luciano.dto.response.ProjectConfigResponse;
import com.luciano.entity.ModelConfig;
import com.luciano.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型配置接口（步骤级配置 + 双 Key 模式）
 * 
 * 配置层级：项目级 > 系统默认
 * 双 Key 模式：platform（平台积分） / user（自带 Key）
 */
@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    // ==================== 公开接口 ====================

    /**
     * 获取系统默认配置（公开，无需认证）
     */
    @GetMapping("/defaults")
    public Result<List<ModelConfig>> getSystemDefaults() {
        return Result.ok(modelConfigService.getSystemDefaults());
    }

    // ==================== 认证接口 ====================

    /**
     * 获取项目的完整步骤配置（含解析信息）
     * 对每个步骤类型，返回项目级覆盖或系统默认，含 Key 来源信息
     */
    @GetMapping("/project/{projectId}")
    public Result<ProjectConfigResponse> getProjectConfigs(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId) {
        return Result.ok(modelConfigService.getProjectConfigs(projectId, userId));
    }

    /**
     * 设置项目步骤配置（创建或更新）
     */
    @PostMapping("/project/{projectId}")
    public Result<ModelConfig> setProjectConfig(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody ModelConfigRequest request) {
        ModelConfig config = modelConfigService.setProjectConfig(
                projectId,
                request.getStepType(),
                request.getProviderId(),
                request.getModelName(),
                request.getProviderSource()
        );
        return Result.ok(config);
    }

    /**
     * 更新模型配置
     */
    @PutMapping("/{id}")
    public Result<ModelConfig> updateConfig(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ModelConfigRequest request) {
        ModelConfig config = modelConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "配置不存在");
        }
        if (request.getProviderId() != null) {
            config.setProviderId(request.getProviderId());
        }
        if (request.getModelName() != null) {
            config.setModelName(request.getModelName());
        }
        if (request.getProviderSource() != null) {
            config.setProviderSource(request.getProviderSource());
        }
        if (request.getConfig() != null) {
            config.setConfig(request.getConfig());
        }
        modelConfigService.updateById(config);
        return Result.ok(config);
    }

    /**
     * 删除模型配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        modelConfigService.removeById(id);
        return Result.ok();
    }

    /**
     * 切换步骤的 Key 来源（platform ↔ user）
     */
    @PostMapping("/{id}/switch-source")
    public Result<ModelConfig> switchProviderSource(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam String providerSource) {
        return Result.ok(modelConfigService.switchProviderSource(id, providerSource));
    }
}