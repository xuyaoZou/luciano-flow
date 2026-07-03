package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 跨参数约束
 * 描述参数之间的依赖/互斥/蕴含关系。
 * 对应 ComfyUI 的 MODE_TEXT2VIDEO / MODE_START_END_FRAME 等映射表。
 */
@Data
@Builder
public class ParamConstraint {

    /** 约束类型 */
    private ConstraintType type;

    /** 涉及的参数名 */
    private List<String> params;

    /** 约束描述（给前端显示错误提示用） */
    private String message;

    /** 条件参数名（当此参数为某值时，约束生效） */
    private String conditionParam;

    /** 条件参数值 */
    private String conditionValue;

    /** 条件：当 conditionParam 等于此值以外的值时，隐藏/禁用指定参数 */
    private String condition;

    /** 是否取反条件（当 conditionParam ≠ conditionValue 时约束生效） */
    @Builder.Default
    private boolean negate = false;

    public enum ConstraintType {
        /** 互斥：参数不能同时出现 */
        MUTEX,
        /** 依赖：A 参数需要 B 参数 */
        REQUIRES,
        /** 蕴含：选择 A 则 B 自动设为某值 */
        IMPLIES,
        /** 自定义约束：由适配器自行解释 */
        CUSTOM
    }
}