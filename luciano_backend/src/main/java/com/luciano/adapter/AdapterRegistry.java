package com.luciano.adapter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 适配器注册中心
 * <p>
 * 借鉴 ComfyUI 的 ComfyExtension 注册机制：
 * - 所有 ModelAdapter 实现通过 Spring @Component 自动注入
 * - 启动时自动注册并构建能力索引
 * - 新增厂商 = 新增一个 @Component 类，零侵入
 * <p>
 * 替代原有的 ModelGateway，增加能力维度：
 * - 旧 ModelGateway 只能按 provider 名路由
 * - 新 AdapterRegistry 支持按能力查找适配器
 * - 支持智能路由（按偏好选择最优适配器）
 */
@Service
@Slf4j
public class AdapterRegistry {

    /** adapter ID → ModelAdapter 实例 */
    private final Map<String, ModelAdapter> adapterMap = new ConcurrentHashMap<>();

    /** Capability → 支持该能力的适配器 ID 列表 */
    private final Map<Capability, List<String>> capabilityIndex = new ConcurrentHashMap<>();

    /** 所有 ModelAdapter 实现（Spring 自动注入） */
    private final List<ModelAdapter> adapterList;

    public AdapterRegistry(List<ModelAdapter> adapterList) {
        this.adapterList = adapterList;
    }

    @PostConstruct
    public void init() {
        // 注册所有适配器
        for (ModelAdapter adapter : adapterList) {
            String id = adapter.getId();
            adapterMap.put(id, adapter);

            // 构建能力索引
            for (Capability cap : adapter.getCapabilities()) {
                capabilityIndex.computeIfAbsent(cap, k -> new ArrayList<>()).add(id);
            }

            log.info("[AdapterRegistry] Registered adapter: id={}, name={}, capabilities={}",
                    id, adapter.getDisplayName(), adapter.getCapabilities());
        }

        log.info("[AdapterRegistry] Total adapters: {}, Total capabilities indexed: {}",
                adapterMap.size(), capabilityIndex.size());
    }

    // ==================== 查询 ====================

    /**
     * 按 ID 获取适配器
     */
    public ModelAdapter getAdapter(String adapterId) {
        ModelAdapter adapter = adapterMap.get(adapterId);
        if (adapter == null) {
            throw new IllegalArgumentException("Unknown adapter: " + adapterId
                    + ". Available: " + adapterMap.keySet());
        }
        return adapter;
    }

    /**
     * 按能力查找所有支持的适配器
     */
    public List<ModelAdapter> getByCapability(Capability capability) {
        List<String> adapterIds = capabilityIndex.getOrDefault(capability, Collections.emptyList());
        return adapterIds.stream()
                .map(adapterMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有适配器
     */
    public Collection<ModelAdapter> getAllAdapters() {
        return Collections.unmodifiableCollection(adapterMap.values());
    }

    // ==================== 智能路由 ====================

    /**
     * 智能路由：按能力 + 偏好选择最优适配器
     *
     * @param capability  需要的能力
     * @param preference  路由偏好
     * @return 最优适配器
     */
    public ModelAdapter route(Capability capability, RoutePreference preference) {
        // 1. 如果用户指定了适配器，优先使用
        if (preference != null && preference.getPreferredAdapter() != null) {
            ModelAdapter adapter = adapterMap.get(preference.getPreferredAdapter());
            if (adapter != null && adapter.supports(capability)) {
                return adapter;
            }
            log.warn("[AdapterRegistry] Preferred adapter '{}' does not support capability '{}', falling back to auto-route",
                    preference.getPreferredAdapter(), capability.getCode());
        }

        // 2. 按能力查找
        List<ModelAdapter> candidates = getByCapability(capability);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No adapter supports capability: " + capability.getCode());
        }

        // 3. 只有一个候选，直接返回
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 4. 按偏好排序
        RoutePreference.Priority priority = preference != null ? preference.getPriority() : RoutePreference.Priority.QUALITY;

        return candidates.stream()
                .max((a, b) -> {
                    // TODO: 未来可根据质量评分、速度评分、成本评分做更精细的路由
                    // 目前按费用等级简单排序
                    int costA = costLevelToInt(a.getCostLevel());
                    int costB = costLevelToInt(b.getCostLevel());
                    return switch (priority) {
                        case QUALITY -> costB - costA;   // 贵的通常质量好
                        case SPEED -> costA - costB;     // 便宜的通常快
                        case COST -> costA - costB;       // 便宜的优先
                    };
                })
                .orElse(candidates.get(0));
    }

    // ==================== 能力矩阵 ====================

    /**
     * 获取能力矩阵（给前端"选择模型"页面用）
     */
    public CapabilityMatrix getCapabilityMatrix() {
        CapabilityMatrix matrix = new CapabilityMatrix();

        // 能力列表
        matrix.setCapabilities(Arrays.stream(Capability.values())
                .map(cap -> {
                    CapabilityMatrix.CapabilityInfo info = new CapabilityMatrix.CapabilityInfo();
                    info.setCode(cap.getCode());
                    info.setDisplayName(cap.getDisplayName());
                    info.setCategory(cap.getCategory());
                    return info;
                })
                .collect(Collectors.toList()));

        // 适配器列表
        matrix.setAdapters(adapterMap.values().stream()
                .map(adapter -> {
                    CapabilityMatrix.AdapterInfo info = new CapabilityMatrix.AdapterInfo();
                    info.setId(adapter.getId());
                    info.setDisplayName(adapter.getDisplayName());
                    info.setDescription(adapter.getDescription());
                    info.setSupportedCapabilities(adapter.getCapabilities().stream()
                            .map(Capability::getCode)
                            .collect(Collectors.toList()));
                    info.setCostLevel(adapter.getCostLevel());
                    return info;
                })
                .collect(Collectors.toList()));

        return matrix;
    }

    // ==================== 辅助 ====================

    private int costLevelToInt(String costLevel) {
        return switch (costLevel.toUpperCase()) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 2;
        };
    }
}