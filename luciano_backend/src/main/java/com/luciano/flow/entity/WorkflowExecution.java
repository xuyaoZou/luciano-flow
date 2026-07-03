package com.luciano.flow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.luciano.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 工作流执行实例
 * <p>
 * 参考设计文档 §4.2 WorkflowExecution 定义。
 * dagSnapshot 冻结执行时的节点和连线，防止模板修改影响执行。
 * nodeResults 记录每个节点的执行结果。
 */
@Data
@TableName(value = "workflow_executions", autoResultMap = true)
public class WorkflowExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private Long userId;

    private Long projectId;

    /** 状态: pending / running / completed / failed / cancelled */
    private String status;

    /** 执行时的 DAG 快照 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String dagSnapshot;

    /** 节点执行结果 { "nodeId": { "status": "completed", "outputUrl": "...", "assetId": 123 } } */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String nodeResults;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    private String errorMsg;

    private OffsetDateTime createdAt;
}