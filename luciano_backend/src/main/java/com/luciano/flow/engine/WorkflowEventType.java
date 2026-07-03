package com.luciano.flow.engine;

/**
 * 工作流执行事件类型
 * <p>
 * SSE 推送的事件类型，前端根据类型渲染不同 UI。
 * 参考设计文档 §8.5 状态反馈。
 */
public enum WorkflowEventType {

    // ==================== 执行级事件 ====================

    /** 工作流执行开始 */
    EXECUTION_STARTED("execution_started"),

    /** 执行完成 */
    EXECUTION_COMPLETED("execution_completed"),

    /** 执行失败 */
    EXECUTION_FAILED("execution_failed"),

    // ==================== 节点级事件 ====================

    /** 节点开始执行 */
    NODE_STARTED("node_started"),

    /** 节点执行完成 */
    NODE_COMPLETED("node_completed"),

    /** 节点执行失败 */
    NODE_FAILED("node_failed"),

    /** 节点执行进度（轮询中） */
    NODE_PROGRESS("node_progress"),

    // ==================== 层级事件 ====================

    /** 某层开始执行 */
    LAYER_STARTED("layer_started"),

    /** 某层执行完成 */
    LAYER_COMPLETED("layer_completed"),

    // ==================== 连线数据流事件 ====================

    /** 上游输出已就绪，传递到下游 */
    DATA_FLOW("data_flow"),

    ;

    private final String code;

    WorkflowEventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}