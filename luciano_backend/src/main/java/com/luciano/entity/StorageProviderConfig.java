package com.luciano.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 存储提供者配置（可插拔：local / s3 / oss / tos）
 * <p>
 * 配置入库，运营可在后台随时改存储策略，不用重启服务。
 */
@Data
@TableName(value = "storage_providers", autoResultMap = true)
public class StorageProviderConfig {

    @TableId
    private Long id;

    /** 存储类型：local / s3 / oss / tos */
    private String providerType;

    /** 显示名 */
    private String name;

    /** 是否默认存储 */
    private Boolean isDefault;

    /** 各 provider 的配置（endpoint/bucket/key/secret/publicUrl 等） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    /** 是否启用 */
    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    // ========== 便捷方法 ==========

    /** 获取配置中的字符串值 */
    public String getConfigString(String key) {
        if (config == null) return null;
        Object val = config.get(key);
        return val != null ? val.toString() : null;
    }

    /** 获取配置中的整数值 */
    public Integer getConfigInt(String key) {
        if (config == null) return null;
        Object val = config.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return null;
    }

    /** 获取公网访问 URL 前缀 */
    public String getPublicUrl() {
        return getConfigString("publicUrl");
    }

    /** 获取本地存储路径 */
    public String getPath() {
        return getConfigString("path");
    }
}