/**
 * Flow 画布状态管理
 * 注意：useVueFlow() 必须在组件 setup 中调用，不能在独立的 composable 中
 * 因为 VueFlow 需要和 <VueFlow> 组件共享同一个实例
 *
 * 所以这个文件只提供纯逻辑函数，Vue Flow 实例由 FlowCanvas.vue 管理
 */
import type { Node, Edge } from '@vue-flow/core'
import type {
  WorkflowNode,
  WorkflowEdge as WfEdge,
  PortDef,
  PortTypeCode,
} from '~/types/flow'
import { PORT_TYPE_COLORS, isSpecialNode } from '~/types/flow'

/** 节点数据结构（Vue Flow 内部） */
export interface FlowNodeData {
  label: string
  type: string
  category: string
  isSpecial: boolean
  adapterId: string | null
  params: Record<string, any>
  inputSlots: PortDef[]
  outputSlots: PortDef[]
  status?: 'idle' | 'running' | 'completed' | 'failed'
  outputUrl?: string
  outputUrls?: string[]  // 多结果 URL 列表
  errorMsg?: string
  // 引用节点字段
  parentNodeId?: string  // 指向父节点 ID
  imageIndex?: number     // 父节点结果索引
}

/** 解析合并端口 ID 到具体 slot name
 * 合并端口格式: "input-merged" / "output-merged"
 * 具体端口格式: "image" / "video" / "prompt" 等
 * 保存到后端时需要具体 slot name
 */
export function resolveHandleToSlot(
  handleId: string | null,
  data: FlowNodeData,
  direction: 'source' | 'target'
): string {
  if (!handleId || handleId === 'output-merged' || handleId === 'input-merged') {
    // 合并端口：取第一个 slot
    const slots = direction === 'source' ? data.outputSlots : data.inputSlots
    return slots?.[0]?.name || ''
  }
  return handleId
}

/** 从后端 Workflow 加载节点和边 */
export function deserializeWorkflow(workflow: { nodes: string; edges: string; lastExecutionResults?: string | null }): {
  nodes: Node[]
  edges: Edge[]
} {
  const wfNodes: WorkflowNode[] = JSON.parse(workflow.nodes)
  const wfEdges: WfEdge[] = JSON.parse(workflow.edges)

  const nodes: Node[] = wfNodes.map(n => {
    // 从 params 恢复持久化数据（outputUrl 媒体引用 + status）
    const params = n.params || {}
    const nodeStatus = params.status === 'completed' ? 'completed' : params.status === 'failed' ? 'failed' : 'idle'
    const outputUrl = params.outputUrl || null
    const outputUrls = params.outputUrls || null
    return {
      id: n.id,
      type: isSpecialNode(n.type) ? 'special' : (n.type === 'reference' ? 'reference' : 'capability'),
      position: { x: n.x, y: n.y },
      data: {
        label: n.type,
        type: n.type,
        category: n.type === 'reference' ? '引用' : '',
        isSpecial: isSpecialNode(n.type),
        adapterId: n.adapterId,
        params,
        inputSlots: n.inputSlots || [],
        outputSlots: n.outputSlots || [],
        status: nodeStatus,
        outputUrl,
        outputUrls,
      } as FlowNodeData,
    }
  })

  const edges: Edge[] = wfEdges.map(e => ({
    id: e.id,
    source: e.sourceNodeId,
    target: e.targetNodeId,
    sourceHandle: e.sourceSlot,
    targetHandle: e.targetSlot,
    type: 'default',
    animated: true,
    data: { dataType: e.dataType },
    style: { stroke: PORT_TYPE_COLORS[e.dataType as PortTypeCode] || '#95A5A6' },
  }))

  return { nodes, edges }
}

/** 从画布序列化为后端格式 */
export function serializeToWorkflow(nodes: Node[], edges: Edge[]): {
  nodes: WorkflowNode[]
  edges: WfEdge[]
} {
  const wfNodes: WorkflowNode[] = nodes.map(n => {
    const data = n.data as FlowNodeData
    // params 里的 status/outputUrl 都是持久化数据，直接保存
    const params = { ...data.params }
    return {
      id: n.id,
      type: data.type,
      adapterId: data.adapterId,
      x: Math.round(n.position.x),
      y: Math.round(n.position.y),
      params,
      inputSlots: data.inputSlots,
      outputSlots: data.outputSlots,
    }
  })

  const wfEdges: WfEdge[] = edges.map(e => ({
    id: e.id,
    sourceNodeId: e.source,
    sourceSlot: e.sourceHandle || '',
    targetNodeId: e.target,
    targetSlot: e.targetHandle || '',
    dataType: (e.data as any)?.dataType || 'VIDEO',
  }))

  return { nodes: wfNodes, edges: wfEdges }
}

/** 创建新节点 */
export function createNode(schema: any, position: { x: number; y: number }): Node {
  const id = `node_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
  return {
    id,
    type: isSpecialNode(schema.type) ? 'special' : (schema.type === 'reference' ? 'reference' : 'capability'),
    position,
    data: {
      label: schema.displayName,
      type: schema.type,
      category: schema.category,
      isSpecial: schema.isSpecial,
      adapterId: null,
      params: {},
      inputSlots: schema.inputSlots ? [...schema.inputSlots] : [],
      outputSlots: schema.outputSlots ? [...schema.outputSlots] : [],
      status: 'idle' as const,
    } as FlowNodeData,
  }
}

/** 创建新边 */
export function createEdge(
  source: string,
  sourceHandle: string | null,
  target: string,
  targetHandle: string | null,
  dataType: string
): Edge {
  const id = `edge_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
  return {
    id,
    source,
    target,
    sourceHandle,
    targetHandle,
    type: 'default',
    animated: true,
    data: { dataType },
    style: { stroke: PORT_TYPE_COLORS[dataType as PortTypeCode] || '#95A5A6' },
  }
}

/** 小地图节点颜色 */
export function miniMapNodeColor(node: any): string {
  const data = node?.data as FlowNodeData
  if (!data) return '#64748b'
  const colors: Record<string, string> = {
    '视频': '#D94A4A', '图片': '#4A90D9', '音频': '#4AD97A',
    '输入': '#4ade80', '输出': '#60a5fa', '控制': '#9B59B6',
  }
  return colors[data.category] || '#64748b'
}