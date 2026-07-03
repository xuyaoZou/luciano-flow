package com.luciano.adapter;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 适配器能力矩阵
 * 描述所有适配器及其支持的能力，供前端"选择模型"页面使用。
 */
@Data
public class CapabilityMatrix {

    /** 所有能力列表 */
    private List<CapabilityInfo> capabilities;

    /** 所有适配器列表 */
    private List<AdapterInfo> adapters;

    @Data
    public static class CapabilityInfo {
        private String code;
        private String displayName;
        private String category;
    }

    @Data
    public static class AdapterInfo {
        private String id;
        private String displayName;
        private String description;
        private List<String> supportedCapabilities;
        private String costLevel;  // LOW / MEDIUM / HIGH
        private Map<String, Object> metadata;  // 额外信息（如官网链接、文档等）
    }
}