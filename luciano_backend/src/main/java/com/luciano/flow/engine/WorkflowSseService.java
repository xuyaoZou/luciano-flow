package com.luciano.flow.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流 SSE 推送服务
 * <p>
 * 管理前端 SSE 连接，在工作流执行过程中实时推送事件。
 * <p>
 * 使用方式：
 * 1. 前端通过 GET /api/v1/workflows/executions/{id}/stream 建立连接
 * 2. WorkflowEngine 执行过程中调用 sendXxx() 方法推送事件
 * 3. 前端根据事件类型更新节点状态（呼吸灯、进度、结果预览）
 * <p>
 * 参考设计文档 §8.5 状态反馈。
 */
@Slf4j
@Service
public class WorkflowSseService {

    private final ObjectMapper objectMapper;

    /** executionId → SseEmitter */
    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public WorkflowSseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建 SSE 连接
     *
     * @param executionId 执行实例 ID
     * @return SseEmitter
     */
    public SseEmitter subscribe(Long executionId) {
        // 超时 30 分钟（长工作流可能需要较长时间）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitter.onCompletion(() -> {
            log.info("SSE 连接完成, executionId={}", executionId);
            emitters.remove(executionId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时, executionId={}", executionId);
            emitters.remove(executionId);
        });
        emitter.onError(ex -> {
            log.error("SSE 连接异常, executionId={}", executionId, ex);
            emitters.remove(executionId);
        });

        emitters.put(executionId, emitter);
        log.info("SSE 订阅建立, executionId={}", executionId);
        return emitter;
    }

    /**
     * 推送事件
     *
     * @param executionId 执行实例 ID
     * @param eventType   事件类型
     * @param data        事件数据
     */
    public void sendEvent(Long executionId, WorkflowEventType eventType, Object data) {
        SseEmitter emitter = emitters.get(executionId);
        if (emitter == null) {
            log.debug("无 SSE 订阅者, executionId={}, event={}", executionId, eventType);
            return;
        }

        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(eventType.getCode())
                    .data(objectMapper.writeValueAsString(data));
            emitter.send(event);
        } catch (IOException e) {
            log.warn("SSE 推送失败, executionId={}, event={}", executionId, eventType, e);
            emitters.remove(executionId);
        }
    }

    /**
     * 完成并关闭 SSE 连接
     */
    public void complete(Long executionId) {
        SseEmitter emitter = emitters.remove(executionId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    // ==================== 便捷方法 ====================

    public void sendExecutionStarted(Long executionId, Long workflowId, int totalNodes) {
        sendEvent(executionId, WorkflowEventType.EXECUTION_STARTED, Map.of(
                "executionId", executionId,
                "workflowId", workflowId,
                "totalNodes", totalNodes
        ));
    }

    public void sendExecutionCompleted(Long executionId) {
        sendEvent(executionId, WorkflowEventType.EXECUTION_COMPLETED, Map.of(
                "executionId", executionId
        ));
        complete(executionId);
    }

    public void sendExecutionFailed(Long executionId, String error) {
        sendEvent(executionId, WorkflowEventType.EXECUTION_FAILED, Map.of(
                "executionId", executionId,
                "error", error
        ));
        complete(executionId);
    }

    public void sendLayerStarted(Long executionId, int layerIndex, java.util.List<String> nodeIds) {
        sendEvent(executionId, WorkflowEventType.LAYER_STARTED, Map.of(
                "executionId", executionId,
                "layerIndex", layerIndex,
                "nodeIds", nodeIds
        ));
    }

    public void sendLayerCompleted(Long executionId, int layerIndex) {
        sendEvent(executionId, WorkflowEventType.LAYER_COMPLETED, Map.of(
                "executionId", executionId,
                "layerIndex", layerIndex
        ));
    }

    public void sendNodeStarted(Long executionId, String nodeId, String nodeType) {
        sendEvent(executionId, WorkflowEventType.NODE_STARTED, Map.of(
                "executionId", executionId,
                "nodeId", nodeId,
                "nodeType", nodeType
        ));
    }

    public void sendNodeCompleted(Long executionId, String nodeId, String nodeType, String outputUrl) {
        sendNodeCompleted(executionId, nodeId, nodeType, outputUrl, null, null, null);
    }

    public void sendNodeCompleted(Long executionId, String nodeId, String nodeType, String outputUrl, Long assetId) {
        sendNodeCompleted(executionId, nodeId, nodeType, outputUrl, assetId, null, null);
    }

    /**
     * 发送节点完成事件（支持多结果）
     * @param outputUrls 所有结果的 display URL 列表（可以为 null）
     * @param assetIds 所有结果的 assetId 列表（可以为 null）
     */
    public void sendNodeCompleted(Long executionId, String nodeId, String nodeType,
                                   String outputUrl, Long assetId,
                                   List<String> outputUrls, List<Long> assetIds) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("executionId", executionId);
        data.put("nodeId", nodeId);
        data.put("nodeType", nodeType);
        data.put("outputUrl", outputUrl != null ? outputUrl : "");
        if (assetId != null) {
            data.put("assetId", assetId);
        }
        // 多结果
        if (outputUrls != null && outputUrls.size() > 1) {
            data.put("outputUrls", outputUrls);
            data.put("assetIds", assetIds);
        }
        sendEvent(executionId, WorkflowEventType.NODE_COMPLETED, data);
    }

    public void sendNodeFailed(Long executionId, String nodeId, String error) {
        sendEvent(executionId, WorkflowEventType.NODE_FAILED, Map.of(
                "executionId", executionId,
                "nodeId", nodeId,
                "error", error
        ));
    }

    public void sendNodeProgress(Long executionId, String nodeId, String status) {
        sendEvent(executionId, WorkflowEventType.NODE_PROGRESS, Map.of(
                "executionId", executionId,
                "nodeId", nodeId,
                "status", status
        ));
    }

    public void sendDataFlow(Long executionId, String sourceNodeId, String sourceSlot,
                            String targetNodeId, String targetSlot, String dataType) {
        sendEvent(executionId, WorkflowEventType.DATA_FLOW, Map.of(
                "executionId", executionId,
                "sourceNodeId", sourceNodeId,
                "sourceSlot", sourceSlot,
                "targetNodeId", targetNodeId,
                "targetSlot", targetSlot,
                "dataType", dataType
        ));
    }
}