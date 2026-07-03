package com.luciano.flow.engine;

import com.luciano.adapter.Capability;
import com.luciano.adapter.ModelAdapter;
import com.luciano.adapter.TaskHandle;
import com.luciano.adapter.TaskStatus;
import com.luciano.adapter.ValidationResult;
import com.luciano.flow.CapabilityPorts;
import com.luciano.flow.OutputRef;
import com.luciano.flow.PortType;
import com.luciano.flow.WorkflowNode;
import com.luciano.adapter.AdapterRegistry;

import java.util.Map;

/**
 * Capability 节点执行器
 * <p>
 * 负责执行适配器能力节点（如 TEXT_TO_VIDEO, IMAGE_TO_VIDEO 等）。
 * 1. 校验参数
 * 2. 解析上游输入（连线传入的值覆盖手动参数）
 * 3. 路由到合适的适配器
 * 4. 提交任务 + 轮询 + 下载结果
 * <p>
 * 参考设计文档 §10.1 适配器层对接。
 */
public class AdapterNodeExecutor {

    private final AdapterRegistry adapterRegistry;

    public AdapterNodeExecutor(AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 解析节点的最终参数
     * <p>
     * 优先级：连线传入的值 > 手动填写的值
     *
     * @param node        节点定义
     * @param upstreamOutputs 上游节点的输出（nodeId → slotName → OutputRef）
     * @return 最终参数 Map
     */
    public Map<String, Object> resolveParams(WorkflowNode node, Map<String, Map<String, OutputRef>> upstreamOutputs) {
        // 先复制手动参数
        Map<String, Object> params = new java.util.HashMap<>();
        if (node.getParams() != null) {
            params.putAll(node.getParams());
        }

        // 连线传入的值覆盖手动参数
        // upstreamOutputs: { sourceNodeId: { sourceSlot: OutputRef } }
        // 需要根据连线映射到当前节点的输入端口
        // 这个逻辑由 WorkflowEngine 在执行前完成，这里只做最终参数组装

        return params;
    }

    /**
     * 校验节点参数
     */
    public ValidationResult validate(Capability capability, Map<String, Object> params) {
        ModelAdapter adapter = adapterRegistry.route(capability, null);
        if (adapter == null) {
            return ValidationResult.errors("没有适配器支持能力: " + capability.getCode());
        }
        return adapter.validate(capability, params);
    }

    /**
     * 提交节点任务
     */
    public TaskHandle submit(Capability capability, Map<String, Object> params, String adapterId) {
        ModelAdapter adapter;
        if (adapterId != null) {
            adapter = adapterRegistry.getAdapter(adapterId);
        } else {
            adapter = adapterRegistry.route(capability, null);
        }
        if (adapter == null) {
            throw new IllegalStateException("没有适配器支持能力: " + capability.getCode());
        }
        return adapter.submit(capability, params);
    }

    /**
     * 轮询任务状态
     */
    public TaskStatus poll(TaskHandle handle, String adapterId) {
        ModelAdapter adapter;
        if (adapterId != null) {
            adapter = adapterRegistry.getAdapter(adapterId);
        } else {
            adapter = adapterRegistry.route(handle.getCapability(), null);
        }
        return adapter.poll(handle);
    }

    /**
     * 下载任务结果
     */
    public com.luciano.adapter.MediaResult download(TaskHandle handle, String adapterId) {
        ModelAdapter adapter;
        if (adapterId != null) {
            adapter = adapterRegistry.getAdapter(adapterId);
        } else {
            adapter = adapterRegistry.route(handle.getCapability(), null);
        }
        return adapter.download(handle);
    }

    /**
     * 根据节点输出端口类型，判断 Capability 的输出类型
     */
    public static PortType getOutputPortType(Capability capability) {
        var outputs = CapabilityPorts.getOutputPorts(capability);
        if (outputs != null && !outputs.isEmpty()) {
            return outputs.get(0).getDataType();
        }
        return PortType.VIDEO; // 默认
    }
}