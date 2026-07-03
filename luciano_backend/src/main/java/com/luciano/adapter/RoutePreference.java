package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

/**
 * 路由偏好
 * 用户提交任务时可以指定偏好，帮助 AdapterRegistry 选择最优适配器。
 */
@Data
@Builder
public class RoutePreference {

    /** 用户指定的适配器 ID（优先级最高，如果指定了则直接使用） */
    private String preferredAdapter;

    /** 路由优先级 */
    private Priority priority;

    /** 最大预算（可选，单位：分） */
    private Integer maxBudgetFen;

    public enum Priority {
        /** 优先质量 */
        QUALITY,
        /** 优先速度 */
        SPEED,
        /** 优先成本 */
        COST
    }
}