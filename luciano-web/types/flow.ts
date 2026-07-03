/**
 * Flow 引擎类型定义
 * 与后端 flow 包对应
 */

/** 端口数据类型 */
export type PortTypeCode =
  | 'image' | 'video' | 'audio' | 'text'
  | 'prompt' | 'negative_prompt'
  | 'model' | 'element' | 'reference' | 'mask'
  | 'number' | 'enum'
  | 'latent' | 'conditioning' | 'mesh'

/** 端口类型信息（含颜色） */
export interface PortTypeDef {
  code: PortTypeCode
  displayName: string
  color: string
}

/** 端口定义 */
export interface PortDef {
  name: string
  displayName: string
  dataType: PortTypeCode
  required: boolean
  multi: boolean
}

/** 工作流节点 */
export interface WorkflowNode {
  id: string
  type: string
  adapterId: string | null
  x: number
  y: number
  params: Record<string, any>
  inputSlots: PortDef[]
  outputSlots: PortDef[]
}

/** 工作流连线 */
export interface WorkflowEdge {
  id: string
  sourceNodeId: string
  sourceSlot: string
  targetNodeId: string
  targetSlot: string
  dataType: string
}

/** 工作流 */
export interface Workflow {
  id: number
  name: string
  description: string | null
  thumbnailUrl: string | null
  category: string | null
  isTemplate: boolean
  userId: number | null
  projectId: number | null
  nodes: string  // JSON
  edges: string  // JSON
  variables: string | null  // JSON
  status: string
  version: number
  createdAt: string
  updatedAt: string
}

/** 工作流执行实例 */
export interface WorkflowExecution {
  id: number
  workflowId: number
  userId: number
  projectId: number | null
  status: string
  dagSnapshot: string  // JSON
  nodeResults: string  // JSON
  startedAt: string | null
  completedAt: string | null
  errorMsg: string | null
  createdAt: string
}

/** SSE 事件类型 */
export type WorkflowEventType =
  | 'execution_started'
  | 'execution_completed'
  | 'execution_failed'
  | 'node_started'
  | 'node_completed'
  | 'node_failed'
  | 'node_progress'
  | 'layer_started'
  | 'layer_completed'
  | 'data_flow'

/** SSE 事件数据 */
export interface WorkflowSseEvent {
  executionId: number
  [key: string]: any
}

/** 节点类型 Schema（来自 /node-types API） */
export interface NodeTypeSchema {
  type: string
  displayName: string
  category: string
  inputSlots: PortDef[]
  outputSlots: PortDef[]
  isSpecial: boolean
}

/** 端口类型颜色映射 */
export const PORT_TYPE_COLORS: { [key: string]: string } = {
  image: '#4A90D9',
  video: '#D94A4A',
  audio: '#4AD97A',
  text: '#D9C94A',
  prompt: '#D9A04A',
  negative_prompt: '#8B4513',
  model: '#9B59B6',
  element: '#FF6B6B',
  reference: '#E91E63',
  mask: '#2ECC71',
  number: '#95A5A6',
  enum: '#95A5A6',
  latent: '#E8B4D9',
  conditioning: '#FF8C00',
  mesh: '#00FF7F',
}

/** 端口类型中文名 */
export const PORT_TYPE_NAMES: { [key: string]: string } = {
  image: '图片',
  video: '视频',
  audio: '音频',
  text: '文本',
  prompt: '提示词',
  negative_prompt: '负向提示词',
  model: '模型',
  element: '主体',
  reference: '参考素材',
  mask: '遮罩',
  number: '数值',
  enum: '枚举',
  latent: '潜空间',
  conditioning: '条件',
  mesh: '3D网格',
}

/** 类别 → 节点边框强调色 */
export const CATEGORY_ACCENTS: { [key: string]: string } = {
  '视频': '#ef4444',
  '图片': '#3b82f6',
  '音频': '#22c55e',
  '输入': '#4ade80',
  '输出': '#60a5fa',
  '控制': '#a855f7',
}

/** 特殊节点类型 */
export const SPECIAL_NODE_TYPES = [
  'ImageInput', 'VideoInput', 'AudioInput', 'TextInput',
  'ImagePreview', 'VideoPreview', 'Switch',
  'ElementSource',
] as const

/** 判断是否为特殊节点 */
export function isSpecialNode(type: string): boolean {
  return (SPECIAL_NODE_TYPES as readonly string[]).includes(type)
}

/** 判断是否为输入节点 */
export function isInputNode(type: string): boolean {
  return ['ImageInput', 'VideoInput', 'AudioInput', 'TextInput', 'ElementSource'].includes(type)
}

/** 判断是否为输出/预览节点 */
export function isOutputNode(type: string): boolean {
  return ['ImagePreview', 'VideoPreview'].includes(type)
}

/** 端口兼容规则 */
export function canConnectPorts(outputType: PortTypeCode, inputType: PortTypeCode): boolean {
  if (outputType === inputType) return true
  // 文本类端口互通：text / prompt / negative_prompt
  const textTypes = ['text', 'prompt', 'negative_prompt']
  if (textTypes.includes(outputType) && textTypes.includes(inputType)) return true
  switch (outputType) {
    case 'image': return inputType === 'reference' || inputType === 'mask'
    case 'video': return inputType === 'reference'
    case 'reference': return inputType === 'image'
    default: return false
  }
}