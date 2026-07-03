package com.luciano.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciano.adapter.Capability;
import com.luciano.flow.CapabilityPorts;
import com.luciano.flow.engine.DagResolver;
import com.luciano.flow.NodeType;
import com.luciano.flow.OutputRef;
import com.luciano.flow.PortType;
import com.luciano.flow.WorkflowEdge;
import com.luciano.flow.WorkflowNode;
import com.luciano.flow.engine.WorkflowEngine;
import com.luciano.flow.entity.Workflow;
import com.luciano.flow.entity.WorkflowExecution;
import com.luciano.flow.repository.WorkflowExecutionMapper;
import com.luciano.flow.repository.WorkflowMapper;
import com.luciano.flow.template.WorkflowTemplate;
import com.luciano.flow.template.WorkflowTemplate.TemplateNode;
import com.luciano.flow.template.WorkflowTemplate.TemplateEdge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作流服务
 * <p>
 * 提供工作流 CRUD + 执行 + 模板管理。
 * 参考设计文档 §7 API 设计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    // ==================== 工作流 CRUD ====================

    /**
     * 创建工作流
     */
    public Workflow createWorkflow(Workflow workflow) {
        // 校验 DAG
        validateDag(workflow);
        workflow.setCreatedAt(OffsetDateTime.now());
        workflow.setUpdatedAt(OffsetDateTime.now());
        if (workflow.getStatus() == null) {
            workflow.setStatus("draft");
        }
        if (workflow.getVersion() == null) {
            workflow.setVersion(1);
        }
        workflowMapper.insert(workflow);
        return workflow;
    }

    /**
     * 获取工作流
     */
    public Workflow getWorkflow(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) return null;

        // 查最近一次执行的 nodeResults，填充到 workflow 中
        LambdaQueryWrapper<WorkflowExecution> qw = new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getWorkflowId, id)
                .orderByDesc(WorkflowExecution::getId)
                .last("LIMIT 1");
        WorkflowExecution lastExec = executionMapper.selectOne(qw);
        if (lastExec != null && lastExec.getNodeResults() != null) {
            workflow.setLastExecutionResults(lastExec.getNodeResults());
        }

        return workflow;
    }

    /**
     * 更新工作流
     */
    public Workflow updateWorkflow(Long id, Workflow updates) {
        Workflow existing = workflowMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("工作流不存在: " + id);
        }

        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getThumbnailUrl() != null) existing.setThumbnailUrl(updates.getThumbnailUrl());
        if (updates.getCategory() != null) existing.setCategory(updates.getCategory());
        if (updates.getNodes() != null) {
            existing.setNodes(updates.getNodes());
            // 校验更新后的 DAG
            try {
                validateDag(existing);
            } catch (Exception e) {
                log.warn("DAG 校验失败, workflowId={}, error={}", id, e.getMessage());
                throw e;
            }
        }
        if (updates.getEdges() != null) existing.setEdges(updates.getEdges());
        if (updates.getVariables() != null) existing.setVariables(updates.getVariables());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());

        existing.setUpdatedAt(OffsetDateTime.now());
        workflowMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除工作流
     */
    public void deleteWorkflow(Long id) {
        // 先删除关联的执行记录，避免外键约束报错
        executionMapper.delete(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getWorkflowId, id));
        workflowMapper.deleteById(id);
    }

    /**
     * 获取项目下的工作流列表
     */
    public List<Workflow> listByProject(Long projectId) {
        return workflowMapper.selectList(
                new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getProjectId, projectId)
                        .orderByDesc(Workflow::getUpdatedAt)
        );
    }

    /**
     * 获取模板列表：预置模板 + 用户保存的模板
     * <p>
     * 预置模板以负数 ID 标识（-1, -2, ...），避免与数据库冲突
     */
    public List<Workflow> listTemplates(String category) {
        List<Workflow> result = new ArrayList<>();

        // 1. 加入预置模板
        long idSeq = -1;
        for (WorkflowTemplate tpl : WorkflowTemplate.ALL) {
            if (category != null && !category.equals(tpl.getCategory())) continue;
            Workflow wf = buildWorkflowFromTemplate(tpl, idSeq--);
            result.add(wf);
        }

        // 2. 加入用户保存的模板
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getIsTemplate, true)
                .orderByAsc(Workflow::getCategory);
        if (category != null) {
            wrapper.eq(Workflow::getCategory, category);
        }
        result.addAll(workflowMapper.selectList(wrapper));

        return result;
    }

    // ==================== 执行 ====================

    /**
     * 执行工作流（异步）
     */
    public Long executeWorkflow(Long workflowId, Long userId) {
        return workflowEngine.executeAsync(workflowId, userId);
    }

    /**
     * 执行单个节点（异步）
     *
     * 只执行指定节点。如果该节点有上游输入，从最近一次成功的执行结果中获取。
     */
    public Long executeNode(Long workflowId, Long userId, String nodeId) {
        return workflowEngine.executeNodeAsync(workflowId, userId, nodeId);
    }

    /**
     * 获取执行实例
     */
    public WorkflowExecution getExecution(Long executionId) {
        return executionMapper.selectById(executionId);
    }

    /**
     * 获取工作流的执行历史
     */
    public List<WorkflowExecution> listExecutions(Long workflowId) {
        return executionMapper.selectList(
                new LambdaQueryWrapper<WorkflowExecution>()
                        .eq(WorkflowExecution::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowExecution::getCreatedAt)
        );
    }

    // ==================== 模板实例化 ====================

    /**
     * 从预置模板创建工作流
     * <p>
     * templateId 为负数时匹配预置模板，正数为数据库模板
     */
    public Workflow instantiateFromTemplate(Long templateId, Long userId, Long projectId, String name) {
        // 预置模板（负数 ID）
        if (templateId < 0) {
            // 从预置模板列表中找到对应的模板
            int idx = (int) (-templateId - 1);
            if (idx < 0 || idx >= WorkflowTemplate.ALL.size()) {
                throw new IllegalArgumentException("预置模板不存在: " + templateId);
            }
            WorkflowTemplate tpl = WorkflowTemplate.ALL.get(idx);
            return instantiateFromPresetTemplate(tpl, userId, projectId, name);
        }

        // 数据库模板
        Workflow template = workflowMapper.selectById(templateId);
        if (template == null || !Boolean.TRUE.equals(template.getIsTemplate())) {
            throw new IllegalArgumentException("模板不存在或不是模板: " + templateId);
        }

        Workflow instance = new Workflow();
        instance.setName(name != null ? name : template.getName() + " (副本)");
        instance.setDescription(template.getDescription());
        instance.setThumbnailUrl(template.getThumbnailUrl());
        instance.setCategory(template.getCategory());
        instance.setIsTemplate(false);
        instance.setUserId(userId);
        instance.setProjectId(projectId);
        instance.setNodes(template.getNodes());
        instance.setEdges(template.getEdges());
        instance.setVariables(template.getVariables());
        instance.setStatus("draft");
        instance.setVersion(1);
        instance.setCreatedAt(OffsetDateTime.now());
        instance.setUpdatedAt(OffsetDateTime.now());

        workflowMapper.insert(instance);
        return instance;
    }

    /**
     * 将预置模板转为 Workflow 对象（用于列表展示，不持久化）
     */
    private Workflow buildWorkflowFromTemplate(WorkflowTemplate tpl, long fakeId) {
        Workflow wf = new Workflow();
        wf.setId(fakeId);
        wf.setName(tpl.getName());
        wf.setDescription(tpl.getDescription());
        wf.setCategory(tpl.getCategory());
        wf.setIsTemplate(true);
        wf.setStatus("draft");

        // 构建节点 JSON
        List<WorkflowNode> nodes = new ArrayList<>();
        List<TemplateNode> tplNodes = tpl.getNodes();
        for (int i = 0; i < tplNodes.size(); i++) {
            TemplateNode tn = tplNodes.get(i);
            WorkflowNode node = new WorkflowNode();
            node.setId("node_" + i);
            node.setType(tn.getType());

            node.setX((int) tn.getX());
            node.setY((int) tn.getY());

            if (tn.isSpecial()) {
                node.setInputSlots(CapabilityPorts.getSpecialInputPorts(tn.getType()));
                node.setOutputSlots(CapabilityPorts.getSpecialOutputPorts(tn.getType()));
            } else if (tn.getCapabilityName() != null) {
                Capability cap = Capability.valueOf(tn.getCapabilityName());
                node.setInputSlots(CapabilityPorts.getInputPorts(cap));
                node.setOutputSlots(CapabilityPorts.getOutputPorts(cap));
            }

            nodes.add(node);
        }

        try {
            wf.setNodes(objectMapper.writeValueAsString(nodes));
            // 边用模板索引构建简单 JSON
            List<WorkflowEdge> edges = new ArrayList<>();
            for (TemplateEdge te : tpl.getEdges()) {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setId("edge_" + te.getSourceIndex() + "_" + te.getTargetIndex());
                edge.setSourceNodeId("node_" + te.getSourceIndex());
                edge.setSourceSlot(te.getSourceSlot());
                edge.setTargetNodeId("node_" + te.getTargetIndex());
                edge.setTargetSlot(te.getTargetSlot());
                edge.setDataType("PROMPT");
                edges.add(edge);
            }
            wf.setEdges(objectMapper.writeValueAsString(edges));
            wf.setVariables("{}");
        } catch (Exception e) {
            log.error("构建模板数据失败", e);
        }

        return wf;
    }

    /**
     * 从预置模板构建工作流
     */
    private Workflow instantiateFromPresetTemplate(WorkflowTemplate tpl, Long userId, Long projectId, String name) {
        List<WorkflowNode> nodes = new ArrayList<>();
        List<TemplateNode> tplNodes = tpl.getNodes();

        for (int i = 0; i < tplNodes.size(); i++) {
            TemplateNode tn = tplNodes.get(i);
            WorkflowNode node = new WorkflowNode();
            node.setId("node_" + i);
            node.setType(tn.getType());

            node.setX((int) tn.getX());
            node.setY((int) tn.getY());

            // 设置端口
            if (tn.isSpecial()) {
                node.setInputSlots(CapabilityPorts.getSpecialInputPorts(tn.getType()));
                node.setOutputSlots(CapabilityPorts.getSpecialOutputPorts(tn.getType()));
            } else if (tn.getCapabilityName() != null) {
                Capability cap = Capability.valueOf(tn.getCapabilityName());
                node.setInputSlots(CapabilityPorts.getInputPorts(cap));
                node.setOutputSlots(CapabilityPorts.getOutputPorts(cap));
            }

            node.setParams(null);
            node.setAdapterId(null);
            nodes.add(node);
        }

        List<WorkflowEdge> edges = new ArrayList<>();
        for (TemplateEdge te : tpl.getEdges()) {
            WorkflowEdge edge = new WorkflowEdge();
            edge.setId("edge_" + te.getSourceIndex() + "_" + te.getTargetIndex());
            edge.setSourceNodeId("node_" + te.getSourceIndex());
            edge.setSourceSlot(te.getSourceSlot());
            edge.setTargetNodeId("node_" + te.getTargetIndex());
            edge.setTargetSlot(te.getTargetSlot());
            edge.setDataType("PROMPT"); // 默认，运行时由端口类型决定
            edges.add(edge);
        }

        try {
            String nodesJson = objectMapper.writeValueAsString(nodes);
            String edgesJson = objectMapper.writeValueAsString(edges);

            Workflow instance = new Workflow();
            instance.setName(name != null ? name : tpl.getName());
            instance.setDescription(tpl.getDescription());
            instance.setCategory(tpl.getCategory());
            instance.setIsTemplate(false);
            instance.setUserId(userId);
            instance.setProjectId(projectId);
            instance.setNodes(nodesJson);
            instance.setEdges(edgesJson);
            instance.setVariables("{}");
            instance.setStatus("draft");
            instance.setVersion(1);
            instance.setCreatedAt(OffsetDateTime.now());
            instance.setUpdatedAt(OffsetDateTime.now());

            workflowMapper.insert(instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("从预置模板创建工作流失败", e);
        }
    }

    /**
     * 保存工作流为模板
     */
    public Workflow saveAsTemplate(Long workflowId, Long userId) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        Workflow template = new Workflow();
        template.setName(workflow.getName() + " (模板)");
        template.setDescription(workflow.getDescription());
        template.setThumbnailUrl(workflow.getThumbnailUrl());
        template.setCategory(workflow.getCategory());
        template.setIsTemplate(true);
        template.setUserId(userId);
        template.setNodes(workflow.getNodes());
        template.setEdges(workflow.getEdges());
        template.setVariables(workflow.getVariables());
        template.setStatus("draft");
        template.setVersion(1);
        template.setCreatedAt(OffsetDateTime.now());
        template.setUpdatedAt(OffsetDateTime.now());

        workflowMapper.insert(template);
        return template;
    }

    // ==================== 节点类型 Schema ====================

    /**
     * 获取所有可用的节点类型及其端口定义
     * 供前端创建节点时查询
     */
    public Map<String, Object> getNodeTypeSchemas() {
        Map<String, Object> schemas = new java.util.LinkedHashMap<>();

        // Capability 节点
        for (Capability cap : Capability.values()) {
            if (cap == Capability.AGENT_CHAT) continue; // Agent 对话不走 Flow
            Map<String, Object> schema = new java.util.LinkedHashMap<>();
            schema.put("type", cap.getCode());
            schema.put("displayName", cap.getDisplayName());
            schema.put("category", cap.getCategory());
            schema.put("inputSlots", CapabilityPorts.getInputPorts(cap));
            schema.put("outputSlots", CapabilityPorts.getOutputPorts(cap));
            schema.put("isSpecial", false);
            schemas.put(cap.getCode(), schema);
        }

        // 特殊节点
        String[] specialTypes = {
                NodeType.IMAGE_INPUT, NodeType.VIDEO_INPUT, NodeType.AUDIO_INPUT, NodeType.TEXT_INPUT,
                NodeType.IMAGE_PREVIEW, NodeType.VIDEO_PREVIEW, NodeType.SWITCH,
                NodeType.ELEMENT_SOURCE
        };
        String[] specialNames = {"图片输入", "视频输入", "音频输入", "文本输入", "图片预览", "视频预览", "条件分支", "主体"};
        String[] specialCategories = {"输入", "输入", "输入", "输入", "输出", "输出", "控制", "输入"};

        for (int i = 0; i < specialTypes.length; i++) {
            Map<String, Object> schema = new java.util.LinkedHashMap<>();
            schema.put("type", specialTypes[i]);
            schema.put("displayName", specialNames[i]);
            schema.put("category", specialCategories[i]);
            schema.put("inputSlots", CapabilityPorts.getSpecialInputPorts(specialTypes[i]));
            schema.put("outputSlots", CapabilityPorts.getSpecialOutputPorts(specialTypes[i]));
            schema.put("isSpecial", true);
            schemas.put(specialTypes[i], schema);
        }

        return schemas;
    }

    // ==================== 校验 ====================

    /**
     * 校验 DAG：解析 + 环检测
     */
    private void validateDag(Workflow workflow) {
        try {
            List<WorkflowNode> nodes = objectMapper.readValue(workflow.getNodes(), new TypeReference<>() {});
            List<WorkflowEdge> edges = objectMapper.readValue(workflow.getEdges(), new TypeReference<>() {});
            // DagResolver.resolve 会自动检测环
            DagResolver.resolve(nodes, edges);
        } catch (IllegalArgumentException e) {
            throw e; // 环检测失败，直接抛出
        } catch (Exception e) {
            throw new IllegalArgumentException("工作流 JSON 解析失败: " + e.getMessage());
        }
    }
}