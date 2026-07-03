package com.luciano.flow.controller;

import com.luciano.flow.entity.Workflow;
import com.luciano.flow.entity.WorkflowExecution;
import com.luciano.flow.service.WorkflowService;
import com.luciano.flow.engine.WorkflowSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 工作流 API 控制器
 * <p>
 * 参考设计文档 §7 API 设计。
 *
 * REST API 汇总：
 * - POST   /api/v1/workflows                     创建工作流
 * - GET    /api/v1/workflows/{id}                获取工作流
 * - PUT    /api/v1/workflows/{id}                更新工作流
 * - DELETE /api/v1/workflows/{id}                删除工作流
 * - GET    /api/v1/workflows?projectId=xxx        项目工作流列表
 * - GET    /api/v1/workflows/templates?category=  模板列表
 * - POST   /api/v1/workflows/{id}/instantiate     从模板实例化
 * - POST   /api/v1/workflows/{id}/save-as-template  保存为模板
 * - POST   /api/v1/workflows/{id}/execute         执行工作流
 * - GET    /api/v1/workflows/executions/{id}      获取执行状态
 * - GET    /api/v1/workflows/{id}/executions      执行历史
 * - GET    /api/v1/workflows/node-types            节点类型 Schema
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowSseService sseService;

    // ==================== 工作流 CRUD ====================

    @PostMapping
    public ResponseEntity<Workflow> createWorkflow(@RequestBody Workflow workflow) {
        Workflow created = workflowService.createWorkflow(workflow);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable Long id) {
        Workflow workflow = workflowService.getWorkflow(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(workflow);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Workflow> updateWorkflow(@PathVariable Long id, @RequestBody Workflow updates) {
        log.info("更新工作流 id={}, nodes长度={}, edges长度={}", 
            id, 
            updates.getNodes() != null ? updates.getNodes().length() : "null",
            updates.getEdges() != null ? updates.getEdges().length() : "null");
        Workflow updated = workflowService.updateWorkflow(id, updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable Long id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Workflow>> listByProject(@RequestParam Long projectId) {
        return ResponseEntity.ok(workflowService.listByProject(projectId));
    }

    // ==================== 模板 ====================

    @GetMapping("/templates")
    public ResponseEntity<List<Workflow>> listTemplates(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(workflowService.listTemplates(category));
    }

    @PostMapping("/{id}/instantiate")
    public ResponseEntity<Workflow> instantiateFromTemplate(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam Long projectId,
            @RequestParam(required = false) String name) {
        Workflow instance = workflowService.instantiateFromTemplate(id, userId, projectId, name);
        return ResponseEntity.ok(instance);
    }

    @PostMapping("/{id}/save-as-template")
    public ResponseEntity<Workflow> saveAsTemplate(
            @PathVariable Long id,
            @RequestParam Long userId) {
        Workflow template = workflowService.saveAsTemplate(id, userId);
        return ResponseEntity.ok(template);
    }

    // ==================== 执行 ====================

    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeWorkflow(
            @PathVariable Long id,
            @RequestParam Long userId) {
        Long executionId = workflowService.executeWorkflow(id, userId);
        return ResponseEntity.ok(Map.of(
                "executionId", executionId,
                "workflowId", id,
                "status", "running"
        ));
    }

    /**
     * 执行单个节点
     * <p>
     * 只执行指定节点，如果该节点有上游输入，尝试从最近一次成功的执行结果中获取。
     * 适用于开发调试场景：修改参数后只重跑一个节点，不需要重新执行整个工作流。
     */
    @PostMapping("/{id}/execute-node")
    public ResponseEntity<Map<String, Object>> executeNode(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam String nodeId) {
        Long executionId = workflowService.executeNode(id, userId, nodeId);
        return ResponseEntity.ok(Map.of(
                "executionId", executionId,
                "workflowId", id,
                "nodeId", nodeId,
                "status", "running"
        ));
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<WorkflowExecution> getExecution(@PathVariable Long executionId) {
        WorkflowExecution execution = workflowService.getExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<WorkflowExecution>> listExecutions(@PathVariable Long id) {
        return ResponseEntity.ok(workflowService.listExecutions(id));
    }

    // ==================== 节点类型 Schema ====================

    @GetMapping("/node-types")
    public ResponseEntity<Map<String, Object>> getNodeTypeSchemas() {
        return ResponseEntity.ok(workflowService.getNodeTypeSchemas());
    }

    // ==================== SSE 实时推送 ====================

    /**
     * 订阅工作流执行事件流
     * <p>
     * 前端通过此端点建立 SSE 连接，实时接收节点执行状态。
     * 支持 Authorization header 和 query param token 两种认证方式
     * （EventSource 不支持自定义 header，所以需要 query param）。
     *
     * @param executionId 执行实例 ID
     * @param token       可选 JWT token（EventSource 场景通过 query param 传入）
     */
    @GetMapping(value = "/executions/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecution(
            @PathVariable Long executionId,
            @RequestParam(required = false) String token) {
        // token 认证在 JwtAuthenticationFilter 中处理
        return sseService.subscribe(executionId);
    }
}