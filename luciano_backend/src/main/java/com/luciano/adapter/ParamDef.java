package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 参数定义
 * 描述一个适配器能力所需的单个参数。
 * 对应 ComfyUI 的 IO.String.Input / IO.Int.Input / IO.Combo.Input 等。
 */
@Data
@Builder
public class ParamDef {

    /** 参数名（snake_case，如 "prompt", "aspect_ratio"） */
    private String name;

    /** 参数类型 */
    private ParamType type;

    /** 显示名称（中文，如 "提示词", "画面比例"） */
    private String displayName;

    /** 参数说明/tooltip */
    private String description;

    /** 默认值 */
    private Object defaultValue;

    /** 最小值（数值类型） */
    private Number min;

    /** 最大值（数值类型） */
    private Number max;

    /** 步长（数值类型） */
    private Number step;

    /** 枚举选项（ENUM 类型） */
    private List<String> options;

    /** 是否必填 */
    @Builder.Default
    private boolean required = false;

    /** 是否多语言（如 prompt 参数需要前端提供翻译提示） */
    @Builder.Default
    private boolean multilingual = false;

    /** 所属分组（如 "运镜控制", "时长设置"），用于前端折叠显示 */
    private String group;

    /** 是否高级参数（默认折叠） */
    @Builder.Default
    private boolean advanced = false;

    /** 条件显示：当指定字段的值等于 showWhenValue 时才显示此参数 */
    private String showWhenField;

    /** 条件显示：目标字段匹配的值 */
    private String showWhenValue;

    /** 参数约束条件（如：仅 mode=pro 时可用） */
    private String condition;
}