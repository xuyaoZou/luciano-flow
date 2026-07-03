package com.luciano.controller;

import com.luciano.entity.StorageProviderConfig;
import com.luciano.service.StorageProviderService;
import com.luciano.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 存储配置管理 API
 * <p>
 * 前端可以通过此 API 查看和切换存储配置，无需重启服务。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageConfigController {

    private final StorageProviderService providerService;
    private final StorageService storageService;

    /**
     * 获取所有存储配置
     */
    @GetMapping("/providers")
    public ResponseEntity<?> listProviders() {
        List<StorageProviderConfig> providers = providerService.listEnabled();
        // 脱敏：隐藏 secretAccessKey
        List<Map<String, Object>> result = providers.stream().map(p -> {
            Map<String, Object> safe = new java.util.LinkedHashMap<>();
            safe.put("id", p.getId());
            safe.put("providerType", p.getProviderType());
            safe.put("name", p.getName());
            safe.put("isDefault", p.getIsDefault());
            safe.put("enabled", p.getEnabled());
            safe.put("createdAt", p.getCreatedAt());
            safe.put("updatedAt", p.getUpdatedAt());
            // config 脱敏
            if (p.getConfig() != null) {
                Map<String, Object> safeConfig = new java.util.LinkedHashMap<>(p.getConfig());
                if (safeConfig.containsKey("secretAccessKey")) {
                    safeConfig.put("secretAccessKey", "******");
                }
                safe.put("config", safeConfig);
            }
            return safe;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前默认存储配置
     */
    @GetMapping("/default")
    public ResponseEntity<?> getDefaultProvider() {
        StorageProviderConfig provider = providerService.getDefault();
        if (provider == null) {
            return ResponseEntity.ok(Map.of("error", "No default storage provider configured"));
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", provider.getId());
        result.put("providerType", provider.getProviderType());
        result.put("name", provider.getName());
        result.put("isDefault", provider.getIsDefault());
        result.put("enabled", provider.getEnabled());
        // 不暴露 config 细节给 default 接口
        return ResponseEntity.ok(result);
    }

    /**
     * 切换默认存储
     * <p>
     * 将指定 provider 设为默认，其他取消默认。
     * 切换后自动刷新缓存。
     */
    @PostMapping("/providers/{id}/default")
    public ResponseEntity<?> setDefault(@PathVariable Long id) {
        StorageProviderConfig target = providerService.getById(id);
        if (target == null || !target.getEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider not found or disabled: " + id));
        }

        // 取消所有其他默认
        List<StorageProviderConfig> all = providerService.list();
        for (StorageProviderConfig p : all) {
            if (p.getIsDefault() && !p.getId().equals(id)) {
                p.setIsDefault(false);
                providerService.updateById(p);
            }
        }

        // 设置目标为默认
        target.setIsDefault(true);
        providerService.updateById(target);

        // 刷新缓存
        providerService.refreshCache();

        log.info("[StorageConfig] Default provider switched to: id={}, type={}, name={}",
                target.getId(), target.getProviderType(), target.getName());

        return ResponseEntity.ok(Map.of(
                "id", target.getId(),
                "providerType", target.getProviderType(),
                "name", target.getName(),
                "isDefault", true
        ));
    }

    /**
     * 更新存储配置（部分更新 config 字段）
     */
    @PatchMapping("/providers/{id}")
    public ResponseEntity<?> updateProvider(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        StorageProviderConfig provider = providerService.getById(id);
        if (provider == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider not found: " + id));
        }

        // 支持更新 name, enabled, config 中的部分字段
        if (updates.containsKey("name")) {
            provider.setName((String) updates.get("name"));
        }
        if (updates.containsKey("enabled")) {
            provider.setEnabled((Boolean) updates.get("enabled"));
        }
        if (updates.containsKey("config")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> newConfig = (Map<String, Object>) updates.get("config");
            if (provider.getConfig() != null) {
                // 合并配置
                Map<String, Object> merged = new java.util.LinkedHashMap<>(provider.getConfig());
                merged.putAll(newConfig);
                provider.setConfig(merged);
            } else {
                provider.setConfig(newConfig);
            }
        }

        providerService.updateById(provider);
        providerService.refreshCache();

        log.info("[StorageConfig] Provider updated: id={}, type={}", provider.getId(), provider.getProviderType());

        return ResponseEntity.ok(Map.of(
                "id", provider.getId(),
                "providerType", provider.getProviderType(),
                "name", provider.getName(),
                "isDefault", provider.getIsDefault(),
                "enabled", provider.getEnabled()
        ));
    }

    /**
     * 新增存储配置
     */
    @PostMapping("/providers")
    public ResponseEntity<?> addProvider(@RequestBody Map<String, Object> body) {
        StorageProviderConfig provider = new StorageProviderConfig();
        provider.setProviderType((String) body.get("providerType"));
        provider.setName((String) body.get("name"));
        provider.setIsDefault(false);
        provider.setEnabled(body.containsKey("enabled") ? (Boolean) body.get("enabled") : true);

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        provider.setConfig(config != null ? config : Map.of());

        providerService.save(provider);
        providerService.refreshCache();

        log.info("[StorageConfig] Provider added: id={}, type={}, name={}",
                provider.getId(), provider.getProviderType(), provider.getName());

        return ResponseEntity.ok(Map.of(
                "id", provider.getId(),
                "providerType", provider.getProviderType(),
                "name", provider.getName()
        ));
    }

    /**
     * 删除存储配置（不能删除默认的）
     */
    @DeleteMapping("/providers/{id}")
    public ResponseEntity<?> deleteProvider(@PathVariable Long id) {
        StorageProviderConfig provider = providerService.getById(id);
        if (provider == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider not found: " + id));
        }
        if (provider.getIsDefault()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete default provider"));
        }

        providerService.removeById(id);
        providerService.refreshCache();

        log.info("[StorageConfig] Provider deleted: id={}", id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id));
    }
}