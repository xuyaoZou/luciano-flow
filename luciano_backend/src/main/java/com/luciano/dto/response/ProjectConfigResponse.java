package com.luciano.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 项目步骤配置响应
 * 每个步骤类型一条配置，包含解析后的信息
 */
@Data
@Builder
public class ProjectConfigResponse {
    private Long projectId;
    private List<StepConfigItem> configs;

    @Data
    @Builder
    public static class StepConfigItem {
        private String stepType;
        private String stepDescription;
        private Long configId;
        private Long providerId;
        private String providerName;
        private String providerDisplayName;
        private String modelName;
        private String providerSource;
        private Boolean isDefault;
        private Boolean hasUserKey;
        private String maskedApiKey;
    }
}