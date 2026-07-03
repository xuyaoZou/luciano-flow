/**
 * Luciano API Client
 * 统一封装后端接口调用
 */
import type { CapabilitySchema, TaskHandle } from '~/types/adapter'

export const useApi = () => {
  const config = useRuntimeConfig()
  const baseUrl = config.public.apiBase

  // 持久化 token
  const tokenCookie = useCookie('luciano-token', { maxAge: 60 * 60 * 24 * 7 })
  const token = useState<string>('auth-token', () => tokenCookie.value || '')

  const request = async <T = any>(path: string, options: {
    method?: string
    body?: any
    params?: any
  } = {}): Promise<T> => {
    // 确保 token 从 cookie 同步（解决 SSR hydration 后 state 为空的问题）
    if (!token.value && tokenCookie.value) {
      token.value = tokenCookie.value
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }
    if (token.value) {
      headers['Authorization'] = `Bearer ${token.value}`
    }

    try {
      const response = await $fetch<{ code: number; message: string; data: T }>(
        `${baseUrl}${path}`,
        {
          method: options.method || 'GET',
          headers,
          body: options.body ? JSON.stringify(options.body) : undefined,
          params: options.params,
        }
      )

      if (response.code !== 0) {
        throw new Error(response.message || '请求失败')
      }
      return response.data
    } catch (error: any) {
      // 401 自动跳登录
      if (error?.statusCode === 401 || error?.response?.status === 401) {
        token.value = ''
        tokenCookie.value = ''
        if (import.meta.client) {
          window.location.href = '/login'
        } else {
          navigateTo('/login')
        }
      }
      throw error
    }
  }

  // ========== Auth ==========

  const login = async (username: string, password: string) => {
    const data = await loginRaw(username, password)
    token.value = data.accessToken
    tokenCookie.value = data.accessToken
    return data
  }

  const loginRaw = async (username: string, password: string) => {
    return request<{ accessToken: string; refreshToken: string; expiresIn: number; user: any }>('/auth/login', {
      method: 'POST',
      body: { username, password },
    })
  }

  const register = async (username: string, password: string, email?: string) => {
    return request<{ accessToken: string; refreshToken: string; user: any }>('/auth/register', {
      method: 'POST',
      body: { username, password, email },
    })
  }

  const getCurrentUser = () => request<any>('/auth/me')

  const logout = () => {
    token.value = ''
    tokenCookie.value = ''
    // 强制跳转登录页
    if (import.meta.client) {
      window.location.href = '/login'
    } else {
      navigateTo('/login')
    }
  }

  // ========== Projects ==========

  const fetchProject = (id: number) => request<any>(`/projects/${id}`)

  const listProjects = (creatorId: number) =>
    request<any[]>('/projects', { params: { creatorId } })

  const createProject = (data: { title: string; type?: string; description?: string }) =>
    request<any>('/projects', { method: 'POST', body: data })

  // ========== Agent Conversations ==========

  const createConversation = (projectId: number, provider = 'xyq') =>
    request<any>('/agent/conversations', {
      method: 'POST',
      body: { projectId, provider },
    })

  const sendMessage = (conversationId: number, message: string) =>
    request<{ conversationId: number; threadId: string; runId: string; status: string }>(
      `/agent/conversations/${conversationId}/message`,
      { method: 'POST', body: { message } }
    )

  const pollRun = (conversationId: number, runId: string) =>
    request<{
      conversationId: number
      status: string
      mediaCount: number
      errorMsg: string | null
      textMessages: Array<{
        messageId: string
        role: string
        text: string
        subType: string
        rawContent: string
        entryIndex: number
      }>
    }>(
      `/agent/conversations/${conversationId}/poll`,
      { params: { runId } }
    )

  const getConversationAssets = (conversationId: number, runId?: string) =>
    request<any[]>(`/agent/conversations/${conversationId}/assets${runId ? '?runId=' + runId : ''}`)

  const getMessages = (conversationId: number) =>
    request<any[]>(`/agent/conversations/${conversationId}/messages`)

  const listConversations = (params: { projectId?: number } = {}) =>
    request<any[]>('/agent/conversations', { params })

  const closeConversation = (conversationId: number) =>
    request<void>(`/agent/conversations/${conversationId}`, { method: 'DELETE' })

  // ========== Media Assets ==========

  const getMediaAssets = (projectId?: number | null, filters?: { mediaType?: string; source?: string }) => {
    const params: Record<string, any> = { ...filters }
    if (projectId) params.projectId = projectId
    return request<any[]>('/media-assets', { params })
  }

  const getMediaAsset = (id: number) => request<any>(`/media-assets/${id}`)

  const linkMediaAsset = (mediaAssetId: number, assetType: string, assetId: number) =>
    request<void>(`/media-assets/${mediaAssetId}/link`, {
      method: 'POST',
      body: { assetType, assetId },
    })

  const unlinkMediaAsset = (mediaAssetId: number, assetType: string, assetId: number) =>
    request<void>(`/media-assets/${mediaAssetId}/link`, {
      method: 'DELETE',
      params: { assetType, assetId },
    })

  const deleteMediaAsset = (id: number) =>
    request<void>(`/media-assets/${id}`, { method: 'DELETE' })

  /** 获取媒体文件 URL：通过 blob URL 方式带 token 请求 */
  const mediaBlobCache = new Map<number, string>()

  const getMediaFileUrl = async (asset: any) => {
    if (!asset) return ''
    if (!asset.id) return asset.url || ''

    // 确保 token 从 cookie 同步（页面刷新时 useState 可能还没初始化）
    if (!token.value && tokenCookie.value) {
      token.value = tokenCookie.value
    }

    // 缓存 blob URL，避免重复请求
    if (mediaBlobCache.has(asset.id)) return mediaBlobCache.get(asset.id)!

    try {
      const headers: Record<string, string> = {}
      if (token.value) headers['Authorization'] = `Bearer ${token.value}`

      const response = await fetch(`${baseUrl}/media/${asset.id}/file`, { headers })
      if (!response.ok) {
        console.warn(`[MediaFile] Failed to load media ${asset.id}: ${response.status}`)
        return asset.url || ''
      }

      const blob = await response.blob()
      const blobUrl = URL.createObjectURL(blob)
      mediaBlobCache.set(asset.id, blobUrl)
      return blobUrl
    } catch (e) {
      console.warn(`[MediaFile] Error loading media ${asset.id}:`, e)
      return asset.url || ''
    }
  }

  /** 释放缓存的 blob URL（组件卸载时调用） */
  const releaseMediaBlobs = () => {
    for (const url of mediaBlobCache.values()) {
      URL.revokeObjectURL(url)
    }
    mediaBlobCache.clear()
  }

  // ========== Adapters ==========

  /** 原始请求（Adapter 接口不走 ApiResponse 解包） */
  const rawRequest = async <T = any>(path: string, options: {
    method?: string
    body?: any
  } = {}): Promise<T> => {
    if (!token.value && tokenCookie.value) token.value = tokenCookie.value
    const headers: Record<string, string> = {}
    if (token.value) headers['Authorization'] = `Bearer ${token.value}`
    // FormData 不设 Content-Type，让浏览器自动设 multipart boundary
    if (!(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json'
    }

    return $fetch<T>(`${baseUrl}${path}`, {
      method: options.method || 'GET',
      headers,
      body: options.body || undefined,
    })
  }

  /** 获取能力矩阵（含适配器列表） */
  const listCapabilities = () =>
    rawRequest<{
      capabilities: Array<{code: string, displayName: string, category: string}>
      adapters: Array<{id: string, displayName: string, supportedCapabilities: string[]}>
    }>('/adapters/capabilities')

  /** 获取指定适配器+能力的 Schema */
  const getSchema = (adapterId: string, capabilityCode: string) =>
    rawRequest<CapabilitySchema>(`/adapters/${adapterId}/schema/${capabilityCode}`)

  /** 获取指定适配器的所有 Schema */
  const getAllSchemas = (adapterId: string) =>
    rawRequest<Record<string, CapabilitySchema>>(`/adapters/${adapterId}/schemas`)

  /** 提交生成任务 */
  const generateTask = (adapterId: string, capabilityCode: string, params: Record<string, any>, projectId?: number) =>
    rawRequest<TaskHandle>('/adapters/generate', {
      method: 'POST',
      body: { adapterId, capability: capabilityCode, params, projectId },
    })

  /** 轮询任务状态 */
  const pollTask = (taskId: string) =>
    rawRequest<TaskHandle>(`/adapters/tasks/${taskId}`)

  // ========== Admin: Operation Logs ==========

  /** 查询操作日志（分页） */
  const getOperationLogs = (params: {
    page?: number
    size?: number
    userId?: number
    adapterId?: string
    capability?: string
    operationType?: string
    responseStatus?: number
    startTime?: string
    endTime?: string
  } = {}) =>
    request<{ records: any[]; total: number; page: number; size: number }>('/admin/operation-logs', { params })

  /** 按 taskId 查询调用链路 */
  const getOperationLogsByTaskId = (taskId: string) =>
    request<any[]>(`/admin/operation-logs/by-task/${taskId}`)

  /** 按 providerTaskId 查询 */
  const getOperationLogsByProviderTaskId = (providerTaskId: string) =>
    request<any[]>(`/admin/operation-logs/by-provider-task/${providerTaskId}`)

  /** 操作日志统计概览 */
  const getOperationLogStats = (params: { startTime?: string; endTime?: string } = {}) =>
    request<any>('/admin/operation-logs/stats', { params })

  // ========== Kling 主体管理 (Element) ==========

  /** 查询自定义主体列表 */
  const listKlingElements = (pageNum = 1, pageSize = 30) =>
    rawRequest<any>(`/adapters/kling/elements?pageNum=${pageNum}&pageSize=${pageSize}`)

  /** 查询官方预设主体列表 */
  const listKlingPresets = (pageNum = 1, pageSize = 30) =>
    rawRequest<any>(`/adapters/kling/elements/presets?pageNum=${pageNum}&pageSize=${pageSize}`)

  /** 创建自定义主体 */
  const createKlingElement = (body: Record<string, any>) =>
    rawRequest<any>('/adapters/kling/elements', { method: 'POST', body })

  /** 查询主体创建任务状态 */
  const pollKlingElementTask = (taskId: string) =>
    rawRequest<any>(`/adapters/kling/elements/${taskId}`)

  /** 删除主体 */
  const deleteKlingElement = (elementId: string) =>
    rawRequest<any>(`/adapters/kling/elements/${elementId}/delete`, { method: 'POST' })

  /** 从画布图片创建主体（手动模式：正面图+参考图） */
  const createKlingElementFromMedia = (body: { element_name: string; element_desc?: string; media_ids: number[]; tag_id?: string }) =>
    rawRequest<any>('/adapters/kling/elements/from-media', { method: 'POST', body })

  /** 一键成为主体（自动生成参考图，异步） */
  const autoCreateKlingElement = (body: { element_name: string; element_desc?: string; media_id: number; tag_id?: string }) =>
    rawRequest<any>('/adapters/kling/elements/auto-from-media', { method: 'POST', body })

  /** 查询一键成为主体异步任务状态 */
  const getAutoElementJobStatus = (jobId: string) =>
    rawRequest<any>(`/adapters/kling/elements/auto-from-media/${jobId}`)

  return {
    // Auth
    login,
    loginRaw,
    register,
    getCurrentUser,
    logout,
    token,
    // Projects
    fetchProject,
    listProjects,
    createProject,
    // Agent
    createConversation,
    sendMessage,
    pollRun,
    getConversationAssets,
    getMessages,
    listConversations,
    closeConversation,
    // Media Assets
    getMediaAssets,
    getMediaAsset,
    linkMediaAsset,
    unlinkMediaAsset,
    deleteMediaAsset,
    getMediaFileUrl,
    releaseMediaBlobs,
    // Adapters
    listCapabilities,
    getSchema,
    getAllSchemas,
    generateTask,
    pollTask,
    rawRequest,
    // Admin
    getOperationLogs,
    getOperationLogsByTaskId,
    getOperationLogsByProviderTaskId,
    getOperationLogStats,
    // Kling Elements
    listKlingElements,
    listKlingPresets,
    createKlingElement,
    pollKlingElementTask,
    deleteKlingElement,
    createKlingElementFromMedia,
    autoCreateKlingElement,
    getAutoElementJobStatus,
  }
}