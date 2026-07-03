package com.luciano.controller;

import com.luciano.common.Result;
import com.luciano.entity.ModelConfig;
import com.luciano.entity.ModelProvider;
import com.luciano.service.ModelConfigService;
import com.luciano.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型服务商和模型配置接口
 */
@RestController
@RequestMapping("/api/v1/model-providers")
@RequiredArgsConstructor
public class ModelProviderController {

    private final ModelProviderService modelProviderService;
    private final ModelConfigService modelConfigService;

    // --- Providers（公开） ---

    /**
     * 列出所有模型服务商（脱敏 Key）
     */
    @GetMapping
    public Result<List<ModelProvider>> listProviders() {
        List<ModelProvider> providers = modelProviderService.list();
        // 脱敏 API Key
        providers.forEach(p -> {
            if (p.getApiKey() != null && p.getApiKey().length() > 8) {
                p.setApiKey(p.getApiKey().substring(0, 4) + "***" + p.getApiKey().substring(p.getApiKey().length() - 4));
            }
        });
        return Result.ok(providers);
    }

    @GetMapping("/{id}")
    public Result<ModelProvider> getProvider(@PathVariable Long id) {
        ModelProvider provider = modelProviderService.getById(id);
        if (provider != null && provider.getApiKey() != null && provider.getApiKey().length() > 8) {
            provider.setApiKey(provider.getApiKey().substring(0, 4) + "***" + provider.getApiKey().substring(provider.getApiKey().length() - 4));
        }
        return Result.ok(provider);
    }

    /**
     * 添加服务商（管理员）
     */
    @PostMapping
    public Result<ModelProvider> createProvider(@RequestBody ModelProvider provider) {
        modelProviderService.save(provider);
        return Result.ok(provider);
    }

    @PutMapping("/{id}")
    public Result<ModelProvider> updateProvider(@PathVariable Long id, @RequestBody ModelProvider provider) {
        provider.setId(id);
        modelProviderService.updateById(provider);
        return Result.ok(modelProviderService.getById(id));
    }
}