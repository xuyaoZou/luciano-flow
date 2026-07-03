package com.luciano.adapter;

import java.util.Map;
import java.util.Set;

/**
 * 模型适配器核心接口
 * <p>
 * 借鉴 ComfyUI 的 ComfyExtension + IO.ComfyNode 模式，
 * 每个厂商实现此接口，声明自己的能力集和参数 Schema。
 * <p>
 * 与旧 SPI 的区别：
 * - 旧 SPI 按介质分接口（VideoGenerator / ImageGenerator），能力粒度太粗
 * - 新接口按能力声明，一个适配器包含该厂商的所有能力
 * - 参数通过 CapabilitySchema 动态下发，前端不硬编码
 * - 新增 validate() 和 estimateCost() 前置校验
 */
public interface ModelAdapter {

    /**
     * 适配器唯一标识（如 "kling", "seedance", "vidu"）
     */
    String getId();

    /**
     * 适配器显示名称（如 "可灵", "Seedance", "Vidu"）
     */
    String getDisplayName();

    /**
     * 适配器描述
     */
    String getDescription();

    /**
     * 声明支持的所有能力
     */
    Set<Capability> getCapabilities();

    /**
     * 判断是否支持某个能力
     */
    default boolean supports(Capability capability) {
        return getCapabilities().contains(capability);
    }

    /**
     * 获取某个能力的参数 Schema（给前端动态渲染用）
     *
     * @param capability 能力类型
     * @return 参数 Schema，不支持的能力返回 null
     */
    CapabilitySchema getSchema(Capability capability);

    /**
     * 获取某个能力的参数 Schema（按模型版本动态返回）
     * <p>
     * 当同一能力有多个版本（如 Seedance 1.x / 2.0）时，
     * 前端先选模型，再按模型拉对应版本的 Schema。
     * 不传 model 时返回默认版本 Schema。
     *
     * @param capability 能力类型
     * @param model      模型标识（可选，用于版本区分）
     * @return 参数 Schema，不支持的能力返回 null
     */
    default CapabilitySchema getSchema(Capability capability, String model) {
        return getSchema(capability);
    }

    /**
     * 校验参数（提交前的前置校验，类似 ComfyUI 的 validate_* 函数）
     *
     * @param capability 能力类型
     * @param params     用户提交的参数
     * @return 校验结果
     */
    ValidationResult validate(Capability capability, Map<String, Object> params);

    /**
     * 提交生成任务
     *
     * @param capability 能力类型
     * @param params     用户提交的参数（已通过 validate 校验）
     * @return 任务句柄，用于后续轮询
     */
    TaskHandle submit(Capability capability, Map<String, Object> params);

    /**
     * 轮询任务状态
     *
     * @param handle 任务句柄
     * @return 任务状态
     */
    TaskStatus poll(TaskHandle handle);

    /**
     * 下载/获取任务结果
     * <p>
     * 仅在 poll 返回 COMPLETED 后调用。
     * 适配器负责将厂商 CDN URL 下载到本地存储（如需要）。
     *
     * @param handle 任务句柄
     * @return 媒体结果
     */
    MediaResult download(TaskHandle handle);

    /**
     * 取消任务（如厂商支持）
     *
     * @param handle 任务句柄
     */
    default void cancel(TaskHandle handle) {
        // 默认空实现，厂商不支持取消时不做任何操作
    }

    /**
     * 费用预估
     *
     * @param capability 能力类型
     * @param params     用户提交的参数
     * @return 费用预估
     */
    CostEstimate estimateCost(Capability capability, Map<String, Object> params);

    /**
     * 费用等级（用于能力矩阵展示）
     */
    default String getCostLevel() {
        return "MEDIUM";
    }
}