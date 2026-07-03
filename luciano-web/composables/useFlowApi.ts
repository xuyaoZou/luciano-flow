/**
 * Flow 引擎 API
 */
import type {
  Workflow,
  WorkflowExecution,
  NodeTypeSchema,
  WorkflowSseEvent,
  WorkflowEventType,
} from '~/types/flow'

export const useFlowApi = () => {
  const api = useApi()

  // ========== 工作流 CRUD ==========

  const createWorkflow = (data: Partial<Workflow>) =>
    api.rawRequest<Workflow>('/workflows', { method: 'POST', body: data })

  const getWorkflow = (id: number) =>
    api.rawRequest<Workflow>(`/workflows/${id}`)

  const updateWorkflow = (id: number, data: Partial<Workflow>) =>
    api.rawRequest<Workflow>(`/workflows/${id}`, { method: 'PUT', body: data })

  const deleteWorkflow = (id: number) =>
    api.rawRequest<void>(`/workflows/${id}`, { method: 'DELETE' })

  const listByProject = (projectId: number) =>
    api.rawRequest<Workflow[]>(`/workflows?projectId=${projectId}`)

  // ========== 模板 ==========

  const listTemplates = (category?: string) =>
    api.rawRequest<Workflow[]>(`/workflows/templates${category ? '?category=' + category : ''}`)

  const instantiateFromTemplate = (templateId: number, userId: number, projectId: number, name?: string) =>
    api.rawRequest<Workflow>(`/workflows/${templateId}/instantiate?userId=${userId}&projectId=${projectId}${name ? '&name=' + encodeURIComponent(name) : ''}`, {
      method: 'POST',
    })

  const saveAsTemplate = (workflowId: number, userId: number) =>
    api.rawRequest<Workflow>(`/workflows/${workflowId}/save-as-template?userId=${userId}`, {
      method: 'POST',
    })

  // ========== 执行 ==========

  const executeWorkflow = (workflowId: number, userId: number) =>
    api.rawRequest<{ executionId: number; workflowId: number; status: string }>(
      `/workflows/${workflowId}/execute?userId=${userId}`,
      { method: 'POST' }
    )

  /** 执行单个节点 */
  const executeNode = (workflowId: number, userId: number, nodeId: string) =>
    api.rawRequest<{ executionId: number; workflowId: number; nodeId: string; status: string }>(
      `/workflows/${workflowId}/execute-node?userId=${userId}&nodeId=${encodeURIComponent(nodeId)}`,
      { method: 'POST' }
    )

  const getExecution = (executionId: number) =>
    api.rawRequest<WorkflowExecution>(`/workflows/executions/${executionId}`)

  const listExecutions = (workflowId: number) =>
    api.rawRequest<WorkflowExecution[]>(`/workflows/${workflowId}/executions`)

  // ========== SSE ==========

  const streamExecution = (executionId: number, handlers: {
    onExecutionStarted?: (data: any) => void
    onExecutionCompleted?: (data: any) => void
    onExecutionFailed?: (data: any) => void
    onNodeStarted?: (data: any) => void
    onNodeCompleted?: (data: any) => void
    onNodeFailed?: (data: any) => void
    onNodeProgress?: (data: any) => void
    onLayerStarted?: (data: any) => void
    onLayerCompleted?: (data: any) => void
    onDataFlow?: (data: any) => void
  }): EventSource => {
    const config = useRuntimeConfig()
    const baseUrl = config.public.apiBase
    // EventSource 不支持自定义 header，JWT token 通过 query param 传递
    const tokenCookie = useCookie('luciano-token')
    const token = tokenCookie.value || ''
    const url = `${baseUrl}/workflows/executions/${executionId}/stream?token=${encodeURIComponent(token)}`

    const eventSource = new EventSource(url)

    const eventMap: Record<string, ((data: any) => void) | undefined> = {
      execution_started: handlers.onExecutionStarted,
      execution_completed: handlers.onExecutionCompleted,
      execution_failed: handlers.onExecutionFailed,
      node_started: handlers.onNodeStarted,
      node_completed: handlers.onNodeCompleted,
      node_failed: handlers.onNodeFailed,
      node_progress: handlers.onNodeProgress,
      layer_started: handlers.onLayerStarted,
      layer_completed: handlers.onLayerCompleted,
      data_flow: handlers.onDataFlow,
    }

    for (const [eventType, handler] of Object.entries(eventMap)) {
      if (handler) {
        eventSource.addEventListener(eventType, (event: MessageEvent) => {
          try {
            const data = JSON.parse(event.data)
            handler(data)
          } catch {
            // 非 JSON 数据忽略
          }
        })
      }
    }

    eventSource.onerror = () => {
      eventSource.close()
    }

    return eventSource
  }

  // ========== 节点类型 Schema ==========

  const getNodeTypes = () =>
    api.rawRequest<Record<string, NodeTypeSchema>>('/workflows/node-types')

  return {
    createWorkflow,
    getWorkflow,
    updateWorkflow,
    deleteWorkflow,
    listByProject,
    listTemplates,
    instantiateFromTemplate,
    saveAsTemplate,
    executeWorkflow,
    executeNode,
    getExecution,
    listExecutions,
    streamExecution,
    getNodeTypes,
  }
}