package com.luciano.flow;

import lombok.Data;

/**
 * 工作流连线
 * <p>
 * JSON 内嵌在 Workflow 中，不独立成表。
 * 参考设计文档 §4.2 WorkflowEdge 定义。
 */
@Data
public class WorkflowEdge {

    /** 连线 UUID */
    private String id;

    /** 源节点 ID */
    private String sourceNodeId;

    /** 源端口名（如 "video"） */
    private String sourceSlot;

    /** 目标节点 ID */
    private String targetNodeId;

    /** 目标端口名（如 "image"） */
    private String targetSlot;

    /** 连线数据类型（PortType code，如 "VIDEO"） */
    private String dataType;
}