package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luciano.dto.response.ProjectConfigResponse;
import com.luciano.entity.ModelConfig;
import com.luciano.entity.ModelProvider;
import com.luciano.entity.StepType;
import com.luciano.entity.UserApiKey;
import com.luciano.repository.mapper.ModelConfigMapper;
import com.luciano.repository.mapper.ModelProviderMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 步骤级模型配置服务
 * 支持双 Key 模式路由：platform Key vs 用户自带 Key
 * 配置优先级：项目级配置 > 系统默认配置
 */
@Service
@Slf4j
public class ModelConfigService extends ServiceImpl<ModelConfigMapper, ModelConfig> {

    private final ModelProviderService modelProviderService;
    private final UserApiKeyService userApiKeyService;
    private final ModelProviderMapper modelProviderMapper;

    public ModelConfigService(ModelProviderService modelProviderService,
                             UserApiKeyService userApiKeyService,
                             ModelProviderMapper modelProviderMapper) {
        this.modelProviderService = modelProviderService;
        this.userApiKeyService = userApiKeyService;
        this.modelProviderMapper = modelProviderMapper;
    }

    // ==================== 查询 ====================

    public List<ModelConfig> listByProjectId(Long projectId) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getProjectId, projectId)
               .orderByAsc(ModelConfig::getStepType);
        return list(wrapper);
    }

    /**
     * 获取系统默认模型配置列表
     */
    public List<ModelConfig> getSystemDefaults() {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getProjectId, 0)
               .eq(ModelConfig::getIsDefault, true)
               .orderByAsc(ModelConfig::getStepType);
        return list(wrapper);
    }

    /**
     * 获取步骤对应的模型配置
     * 优先级：项目级配置 > 系统默认配置
     */
    public ModelConfig getConfigForStep(Long projectId, String stepType) {
        // 1. 查项目级配置
        ModelConfig projectConfig = getDefaultConfig(projectId, stepType);
        if (projectConfig != null) {
            return projectConfig;
        }
        // 2. 查系统默认配置
        return getSystemDefault(stepType);
    }

    public ModelConfig getDefaultConfig(Long projectId, String stepType) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getProjectId, projectId)
               .eq(ModelConfig::getStepType, stepType)
               .eq(ModelConfig::getIsDefault, true);
        return getOne(wrapper);
    }

    private ModelConfig getSystemDefault(String stepType) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getProjectId, 0)
               .eq(ModelConfig::getStepType, stepType)
               .eq(ModelConfig::getIsDefault, true);
        return getOne(wrapper);
    }

    // ==================== 解析配置（双 Key 路由） ====================

    /**
     * 解析模型配置，获取实际可用的 Key 和 BaseUrl
     * 核心路由逻辑：根据 provider_source 决定走平台 Key 还是用户自带 Key
     */
    public ResolvedConfig resolveConfig(Long projectId, String stepType, Long userId) {
        ModelConfig config = getConfigForStep(projectId, stepType);
        if (config == null) {
            throw new IllegalArgumentException("未配置步骤 [" + stepType + "] 的模型，请先配置模型");
        }

        ModelProvider provider = modelProviderService.getById(config.getProviderId());
        if (provider == null) {
            throw new IllegalArgumentException("模型服务商不存在，ID: " + config.getProviderId());
        }
        String providerName = provider.getName();

        if ("user".equals(config.getProviderSource())) {
            // 用户自带 Key
            String userKey = userApiKeyService.getDecryptedKey(userId, providerName);
            if (userKey == null) {
                throw new IllegalArgumentException("请先配置 [" + provider.getDisplayName() + "] 的 API Key");
            }
            String userBaseUrl = userApiKeyService.getUserBaseUrl(userId, providerName);

            return ResolvedConfig.builder()
                    .provider(providerName)
                    .apiKey(userKey)
                    .baseUrl(userBaseUrl != null ? userBaseUrl : provider.getBaseUrl())
                    .providerSource("user")
                    .modelName(config.getModelName())
                    .build();
        } else {
            // 平台 Key
            return ResolvedConfig.builder()
                    .provider(providerName)
                    .apiKey(provider.getApiKey())
                    .baseUrl(provider.getBaseUrl())
                    .providerSource("platform")
                    .modelName(config.getModelName())
                    .build();
        }
    }

    // ==================== 项目级配置管理 ====================

    /**
     * 获取项目的完整步骤配置（含解析信息）
     * 对每个步骤类型，返回项目级覆盖或系统默认
     */
    public ProjectConfigResponse getProjectConfigs(Long projectId, Long userId) {
        // 获取所有步骤类型的有效配置
        List<ModelConfig> projectConfigs = listByProjectId(projectId);
        Map<String, ModelConfig> projectMap = projectConfigs.stream()
                .collect(Collectors.toMap(ModelConfig::getStepType, c -> c, (a, b) -> a));

        List<ProjectConfigResponse.StepConfigItem> items = new ArrayList<>();
        for (StepType stepType : StepType.values()) {
            ModelConfig config = getConfigForStep(projectId, stepType.getCode());
            if (config == null) continue;

            ModelProvider provider = modelProviderService.getById(config.getProviderId());
            if (provider == null) continue;

            // 检查用户是否有自带 Key
            boolean hasUserKey = false;
            String maskedApiKey = null;
            if (userId != null) {
                String decryptedKey = userApiKeyService.getDecryptedKey(userId, provider.getName());
                hasUserKey = decryptedKey != null;
                if (hasUserKey) {
                    maskedApiKey = userApiKeyService.getMaskedKey(userId, provider.getName());
                }
            }
            if (!hasUserKey && provider.getApiKey() != null && provider.getApiKey().length() > 8) {
                maskedApiKey = provider.getApiKey().substring(0, 4) + "***" + provider.getApiKey().substring(provider.getApiKey().length() - 4);
            }

            items.add(ProjectConfigResponse.StepConfigItem.builder()
                    .stepType(stepType.getCode())
                    .stepDescription(stepType.getDescription())
                    .configId(config.getId())
                    .providerId(provider.getId())
                    .providerName(provider.getName())
                    .providerDisplayName(provider.getDisplayName())
                    .modelName(config.getModelName())
                    .providerSource(config.getProviderSource())
                    .isDefault(config.getIsDefault())
                    .hasUserKey(hasUserKey)
                    .maskedApiKey(maskedApiKey)
                    .build());
        }

        return ProjectConfigResponse.builder()
                .projectId(projectId)
                .configs(items)
                .build();
    }

    /**
     * 设置项目步骤配置（创建或更新）
     */
    @org.springframework.transaction.annotation.Transactional
    public ModelConfig setProjectConfig(Long projectId, String stepType, Long providerId,
                                        String modelName, String providerSource) {
        // 验证步骤类型
        StepType st = StepType.fromCode(stepType);
        // 验证服务商存在
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("模型服务商不存在，ID: " + providerId);
        }
        // 验证 providerSource
        if (providerSource != null && !"platform".equals(providerSource) && !"user".equals(providerSource)) {
            throw new IllegalArgumentException("providerSource 只能是 platform 或 user");
        }

        // 查找是否已有项目级配置
        ModelConfig existing = getDefaultConfig(projectId, stepType);
        if (existing != null) {
            // 更新
            existing.setProviderId(providerId);
            if (modelName != null) existing.setModelName(modelName);
            if (providerSource != null) existing.setProviderSource(providerSource);
            updateById(existing);
            return existing;
        } else {
            // 创建
            ModelConfig config = new ModelConfig();
            config.setProjectId(projectId);
            config.setStepType(stepType);
            config.setProviderId(providerId);
            config.setModelName(modelName != null ? modelName : "");
            config.setIsDefault(true);
            config.setProviderSource(providerSource != null ? providerSource : "platform");
            config.setConfig("{}");
            save(config);
            return config;
        }
    }

    /**
     * 切换步骤的 Key 来源（platform ↔ user）
     */
    @org.springframework.transaction.annotation.Transactional
    public ModelConfig switchProviderSource(Long configId, String providerSource) {
        if (!"platform".equals(providerSource) && !"user".equals(providerSource)) {
            throw new IllegalArgumentException("providerSource 只能是 platform 或 user");
        }
        ModelConfig config = getById(configId);
        if (config == null) {
            throw new IllegalArgumentException("配置不存在，ID: " + configId);
        }
        config.setProviderSource(providerSource);
        updateById(config);
        return config;
    }

    /**
     * 解析后的配置（包含实际 API Key）
     */
    @Data
    @Builder
    public static class ResolvedConfig {
        private String provider;
        private String apiKey;
        private String baseUrl;
        private String providerSource;
        private String modelName;
    }

    /**
     * 通过 providerId 获取 provider name
     */
    public String getProviderName(Long providerId) {
        ModelProvider provider = modelProviderService.getById(providerId);
        return provider != null ? provider.getName() : null;
    }
}