package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.entity.StorageProviderConfig;
import com.luciano.mapper.StorageProviderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储提供者服务
 * <p>
 * 管理存储配置，缓存默认 provider，支持热更新。
 */
@Slf4j
@Service
public class StorageProviderService extends ServiceImpl<StorageProviderMapper, StorageProviderConfig> {

    /** 缓存：默认 provider */
    private final ConcurrentHashMap<String, StorageProviderConfig> defaultCache = new ConcurrentHashMap<>();

    /**
     * 获取默认存储提供者
     */
    public StorageProviderConfig getDefault() {
        return defaultCache.computeIfAbsent("default", k -> {
            StorageProviderConfig provider = getOne(new LambdaQueryWrapper<StorageProviderConfig>()
                    .eq(StorageProviderConfig::getIsDefault, true)
                    .eq(StorageProviderConfig::getEnabled, true)
                    .last("LIMIT 1"));
            if (provider == null) {
                log.warn("[StorageProvider] No default provider found, falling back to first enabled");
                provider = getOne(new LambdaQueryWrapper<StorageProviderConfig>()
                        .eq(StorageProviderConfig::getEnabled, true)
                        .last("LIMIT 1"));
            }
            if (provider != null) {
                log.info("[StorageProvider] Default: id={}, type={}, name={}", provider.getId(), provider.getProviderType(), provider.getName());
            }
            return provider;
        });
    }

    /**
     * 根据 ID 获取提供者
     */
    public StorageProviderConfig getById(Long id) {
        if (id == null) return getDefault();
        StorageProviderConfig provider = super.getById(id);
        if (provider == null || !provider.getEnabled()) {
            log.warn("[StorageProvider] Provider id={} not found or disabled, falling back to default", id);
            return getDefault();
        }
        return provider;
    }

    /**
     * 获取所有启用的提供者
     */
    public List<StorageProviderConfig> listEnabled() {
        return list(new LambdaQueryWrapper<StorageProviderConfig>()
                .eq(StorageProviderConfig::getEnabled, true)
                .orderByDesc(StorageProviderConfig::getIsDefault)
                .orderByAsc(StorageProviderConfig::getId));
    }

    /**
     * 刷新缓存（配置变更后调用）
     */
    public void refreshCache() {
        defaultCache.clear();
        log.info("[StorageProvider] Cache cleared");
    }
}