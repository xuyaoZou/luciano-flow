/**
 * useAdapter — 适配器动态表单核心 Composable
 *
 * 管理：适配器选择 → 能力选择 → Schema 加载 → 表单渲染 → 提交生成 → 轮询结果
 */
import type { CapabilityInfo, CapabilitySchema, ParamSchema, TaskHandle, TaskStatus } from '~/types/adapter'

interface AdapterInfo {
  id: string
  displayName: string
  supportedCapabilities: string[]
}

export const useAdapter = () => {
  const { listCapabilities, getSchema, generateTask, pollTask } = useApi()

  // ========== 适配器 & 能力 ==========
  const adapters = ref<AdapterInfo[]>([])
  const capabilities = ref<CapabilityInfo[]>([])
  const selectedAdapter = ref<string>('')
  const selectedCapability = ref<string>('')

  // ========== Schema ==========
  const schema = ref<CapabilitySchema | null>(null)
  const schemaLoading = ref(false)

  // ========== 表单参数 ==========
  const formParams = ref<Record<string, any>>({})

  // ========== 生成任务 ==========
  const taskHandle = ref<TaskHandle | null>(null)
  const taskPolling = ref(false)
  const taskPollTimer = ref<ReturnType<typeof setInterval> | null>(null)

  // ========== 错误 ==========
  const error = ref<string | null>(null)

  // ===== 计算属性 =====

  /** 当前适配器支持的能力列表 */
  const adapterCapabilities = computed(() => {
    if (!selectedAdapter.value) return []
    const adapter = adapters.value.find(a => a.id === selectedAdapter.value)
    if (!adapter) return []
    return capabilities.value.filter(c => adapter.supportedCapabilities.includes(c.code))
  })

  /** 合并所有参数（必填 + 可选） */
  const allParams = computed(() => {
    if (!schema.value) return []
    return [...schema.value.requiredParams, ...schema.value.optionalParams]
  })

  /** 按组分类的参数 */
  const groupedParams = computed(() => {
    const groups: Record<string, ParamSchema[]> = {}
    for (const p of allParams.value) {
      const g = p.group || '其他'
      if (!groups[g]) groups[g] = []
      groups[g].push(p)
    }
    return groups
  })

  /** 表单是否可提交 */
  const canSubmit = computed(() => {
    if (!schema.value || !selectedAdapter.value || !selectedCapability.value) return false
    return schema.value.requiredParams.every(p => {
      const val = formParams.value[p.name]
      return val !== undefined && val !== null && val !== ''
    })
  })

  // ===== Actions =====

  /** 加载适配器和能力列表 */
  const loadAdapters = async () => {
    try {
      error.value = null
      const data = await listCapabilities()
      adapters.value = data.adapters
      capabilities.value = data.capabilities
    } catch (e: any) {
      error.value = e.message || '加载适配器失败'
    }
  }

  /** 加载指定能力的 Schema */
  const loadSchema = async (adapterId: string, capabilityCode: string) => {
    try {
      schemaLoading.value = true
      error.value = null
      const data = await getSchema(adapterId, capabilityCode)
      schema.value = data

      // 用默认值初始化表单
      formParams.value = {}
      for (const p of data.requiredParams) {
        formParams.value[p.name] = p.defaultValue ?? (p.type === 'ENUM' ? (p.options?.[0] ?? '') : '')
      }
      for (const p of data.optionalParams) {
        if (p.defaultValue !== null && p.defaultValue !== undefined) {
          formParams.value[p.name] = p.defaultValue
        }
      }
    } catch (e: any) {
      error.value = e.message || '加载 Schema 失败'
      schema.value = null
    } finally {
      schemaLoading.value = false
    }
  }

  /** 选择能力时自动加载 Schema */
  const selectCapability = async (capabilityCode: string) => {
    selectedCapability.value = capabilityCode
    taskHandle.value = null
    error.value = null
    if (selectedAdapter.value && capabilityCode) {
      await loadSchema(selectedAdapter.value, capabilityCode)
    }
  }

  /** 选择适配器 */
  const selectAdapter = (adapterId: string) => {
    selectedAdapter.value = adapterId
    selectedCapability.value = ''
    schema.value = null
    formParams.value = {}
    taskHandle.value = null
    error.value = null
  }

  /** 提交生成任务 */
  const submitGenerate = async (projectId?: number) => {
    if (!canSubmit.value) return

    try {
      error.value = null
      // 过滤掉空值和 undefined
      const params: Record<string, any> = {}
      for (const [k, v] of Object.entries(formParams.value)) {
        if (v !== undefined && v !== null && v !== '') {
          params[k] = v
        }
      }

      const handle = await generateTask(selectedAdapter.value, selectedCapability.value, params, projectId)
      taskHandle.value = handle

      // 开始轮询
      startPolling(handle.taskId)
    } catch (e: any) {
      error.value = e.message || '提交失败'
    }
  }

  /** 开始轮询任务状态 */
  const startPolling = (taskId: string) => {
    stopPolling()
    taskPolling.value = true

    const poll = async () => {
      try {
        const handle = await pollTask(taskId)
        taskHandle.value = handle

        if (handle.status === 'COMPLETED' || handle.status === 'FAILED') {
          stopPolling()
        }
      } catch (e: any) {
        console.error('Poll task error:', e)
      }
    }

    poll()
    taskPollTimer.value = setInterval(poll, 5000)
  }

  /** 停止轮询 */
  const stopPolling = () => {
    taskPolling.value = false
    if (taskPollTimer.value) {
      clearInterval(taskPollTimer.value)
      taskPollTimer.value = null
    }
  }

  /** 重置所有状态 */
  const reset = () => {
    stopPolling()
    selectedAdapter.value = ''
    selectedCapability.value = ''
    schema.value = null
    formParams.value = {}
    taskHandle.value = null
    error.value = null
  }

  onUnmounted(() => {
    stopPolling()
  })

  return {
    // State
    adapters,
    capabilities,
    selectedAdapter,
    selectedCapability,
    schema,
    schemaLoading,
    formParams,
    taskHandle,
    taskPolling,
    error,
    // Computed
    adapterCapabilities,
    allParams,
    groupedParams,
    canSubmit,
    // Actions
    loadAdapters,
    loadSchema,
    selectAdapter,
    selectCapability,
    submitGenerate,
    stopPolling,
    reset,
  }
}