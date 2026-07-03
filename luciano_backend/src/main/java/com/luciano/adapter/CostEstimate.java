package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用预估
 * 对应 ComfyUI 的 PriceBadge 机制。
 */
@Data
@Builder
public class CostEstimate {

    /** 费用金额 */
    private BigDecimal amount;

    /** 货币单位（如 CNY, USD） */
    private String currency;

    /** 计费模型描述（如 "按次计费", "按时长计费"） */
    private String billingModel;

    /** 人类可读的费用描述（如 "约 ¥0.5/次"） */
    private String displayText;

    /** 预估耗时（毫秒） */
    private Integer estimatedDurationMs;
}