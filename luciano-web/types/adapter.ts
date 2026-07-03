/**
 * Adapter 相关类型定义
 */

/** 能力信息 */
export interface CapabilityInfo {
  code: string
  displayName: string
  category: string
}

/** 参数 Schema */
export interface ParamSchema {
  name: string
  type: 'STRING' | 'INTEGER' | 'FLOAT' | 'ENUM' | 'BOOLEAN' | 'IMAGE_URL' | 'VIDEO_URL' | 'ELEMENT_LIST'
  displayName: string
  description: string | null
  defaultValue: any
  min: number | null
  max: number | null
  step: number | null
  options: string[] | null
  required: boolean
  multilingual: boolean
  group: string
  advanced: boolean
  showWhenField: string | null
  showWhenValue: string | null
  condition: string | null
}

/** Schema 约束 */
export interface SchemaConstraint {
  type: string
  params: string[]
  message: string
  conditionParam?: string
  conditionValue?: string
  negate?: boolean
}

/** 能力 Schema */
export interface CapabilitySchema {
  adapterId: string
  capability: string
  displayName: string
  description: string
  requiredParams: ParamSchema[]
  optionalParams: ParamSchema[]
  constraints: SchemaConstraint[]
  outputFormats: string[]
  estimatedDurationMs: number | null
  costHint: string | null
}

/** 任务状态 */
export type TaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

/** 任务句柄 */
export interface TaskHandle {
  taskId: string
  dbTaskId: number | null
  adapterId: string
  capability: string | null
  providerTaskId: string
  status: TaskStatus
  resultUrl: string | null
  localPath: string | null
  mediaAssetId: number | null
  durationMs: number | null
  resolution: string | null
  errorMsg: string | null
  costHint: string | null
}