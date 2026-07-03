package com.luciano.flow.engine;

import com.luciano.flow.WorkflowEdge;
import com.luciano.flow.WorkflowNode;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DAG 解析器
 * <p>
 * 负责将工作流的节点和连线解析为可执行的 DAG（有向无环图）。
 * 核心功能：
 * 1. 拓扑排序 — 确定节点执行顺序
 * 2. 环检测 — 有环则拒绝执行
 * 3. 分层 — 同层节点可并行执行
 * <p>
 * 参考设计文档 §6 执行引擎。
 */
public class DagResolver {

    /**
     * 解析工作流为执行层列表
     *
     * @param nodes 工作流节点列表
     * @param edges 工作流连线列表
     * @return 分层结果，每层包含可并行执行的节点 ID
     * @throws IllegalArgumentException 如果存在环
     */
    public static List<List<String>> resolve(List<WorkflowNode> nodes, List<WorkflowEdge> edges)
            throws IllegalArgumentException {

        // 1. 构建邻接表和入度表
        Map<String, Set<String>> adjacency = new HashMap<>();  // nodeId → 下游节点集合
        Map<String, Integer> inDegree = new HashMap<>();        // nodeId → 入度

        for (WorkflowNode node : nodes) {
            adjacency.put(node.getId(), new HashSet<>());
            inDegree.put(node.getId(), 0);
        }

        for (WorkflowEdge edge : edges) {
            adjacency.computeIfAbsent(edge.getSourceNodeId(), k -> new HashSet<>())
                    .add(edge.getTargetNodeId());
            inDegree.merge(edge.getTargetNodeId(), 1, Integer::sum);
        }

        // 2. 环检测 + 拓扑排序（Kahn 算法）
        List<List<String>> layers = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        // 入度为 0 的节点作为第一层
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int processedCount = 0;

        while (!queue.isEmpty()) {
            // 当前层所有节点
            List<String> layer = new ArrayList<>();
            int layerSize = queue.size();
            for (int i = 0; i < layerSize; i++) {
                String nodeId = queue.poll();
                layer.add(nodeId);
                processedCount++;

                // 减少下游节点的入度
                for (String downstream : adjacency.getOrDefault(nodeId, Set.of())) {
                    int newDegree = inDegree.get(downstream) - 1;
                    inDegree.put(downstream, newDegree);
                    if (newDegree == 0) {
                        queue.add(downstream);
                    }
                }
            }
            layers.add(layer);
        }

        // 3. 环检测：如果处理的节点数 < 总节点数，说明有环
        if (processedCount < nodes.size()) {
            Set<String> processed = layers.stream().flatMap(List::stream).collect(Collectors.toSet());
            List<String> cycleNodes = nodes.stream()
                    .map(WorkflowNode::getId)
                    .filter(id -> !processed.contains(id))
                    .toList();
            throw new IllegalArgumentException("工作流存在环，无法执行。环中节点: " + cycleNodes);
        }

        return layers;
    }

    /**
     * 获取某个节点的上游节点 ID 集合
     */
    public static Set<String> getUpstreamNodeIds(String nodeId, List<WorkflowEdge> edges) {
        return edges.stream()
                .filter(e -> e.getTargetNodeId().equals(nodeId))
                .map(WorkflowEdge::getSourceNodeId)
                .collect(Collectors.toSet());
    }

    /**
     * 获取某个节点的下游节点 ID 集合
     */
    public static Set<String> getDownstreamNodeIds(String nodeId, List<WorkflowEdge> edges) {
        return edges.stream()
                .filter(e -> e.getSourceNodeId().equals(nodeId))
                .map(WorkflowEdge::getTargetNodeId)
                .collect(Collectors.toSet());
    }

    /**
     * 获取指向某个节点某个输入端口的所有连线
     */
    public static List<WorkflowEdge> getIncomingEdges(String nodeId, String slotName, List<WorkflowEdge> edges) {
        return edges.stream()
                .filter(e -> e.getTargetNodeId().equals(nodeId) && e.getTargetSlot().equals(slotName))
                .toList();
    }

    /**
     * 获取从某个节点某个输出端口出发的所有连线
     */
    public static List<WorkflowEdge> getOutgoingEdges(String nodeId, String slotName, List<WorkflowEdge> edges) {
        return edges.stream()
                .filter(e -> e.getSourceNodeId().equals(nodeId) && e.getSourceSlot().equals(slotName))
                .toList();
    }
}