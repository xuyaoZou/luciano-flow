package com.luciano.flow;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流节点
 * <p>
 * JSON 内嵌在 Workflow 中，不独立成表。
 * 参考设计文档 §4.2 WorkflowNode 定义。
 * <p>
 * type 字段取值：
 * - Capability 枚举值（如 "TEXT_TO_VIDEO"）→ 对应适配器能力节点
 * - 特殊节点类型（如 "ImageInput", "VideoPreview"）→ 基础设施节点，不经过适配器
 */
@Data
public class WorkflowNode {

    /** 前端生成的 UUID（如 "node_1"） */
    private String id;

    /** 节点类型（Capability 枚举值或特殊节点类型名） */
    private String type;

    /** 指定适配器 ID，null 表示自动路由 */
    private String adapterId;

    /** 画布 X 坐标 */
    private Integer x;

    /** 画布 Y 坐标 */
    private Integer y;

    /** 参数值（用户手动输入的值，连线传入的值优先级更高） */
    private Map<String, Object> params;

    /** 输入端口声明 */
    private List<PortDef> inputSlots;

    /** 输出端口声明 */
    private List<PortDef> outputSlots;
}