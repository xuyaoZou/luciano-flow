package com.luciano.adapter;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 能力参数 Schema
 * 描述某个适配器的某个能力所需的所有参数定义。
 * 前端根据此 Schema 动态渲染参数表单。
 * 对应 ComfyUI 的 INPUT_TYPES 机制。
 */
@Data
@Builder
public class CapabilitySchema {

    /** 所属适配器 ID */
    private String adapterId;

    /** 能力标识 */
    private Capability capability;

    /** 能力显示名称 */
    private String displayName;

    /** 能力描述 */
    private String description;

    /** 必填参数列表 */
    private List<ParamDef> requiredParams;

    /** 可选参数列表（前端默认折叠） */
    private List<ParamDef> optionalParams;

    /** 跨参数约束 */
    private List<ParamConstraint> constraints;

    /** 支持的输出格式（如 ["mp4", "webm"]） */
    private List<String> outputFormats;

    /** 典型生成耗时（毫秒），用于前端展示预估时间 */
    private Integer estimatedDurationMs;

    /** 计费信息提示（如 "约 ¥0.5/次"） */
    private String costHint;
}