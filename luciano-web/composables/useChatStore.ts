/**
 * Chat Store — 维护聊天上下文，切页面不丢
 * 用 useState 实现跨组件持久化（SSR 安全）
 */
export const useChatStore = () => {
  interface Message {
    id: string | number
    role: 'user' | 'assistant'
    content?: string
    text?: string
    mediaItems?: any[]
    status?: 'processing' | 'completed' | 'failed'
    errorMsg?: string
    runId?: string
  }

  const messages = useState<Message[]>('chat-messages', () => [])
  const conversationId = useState<number | null>('chat-conversation-id', () => null)
  const activeProjectId = useState<number | null>('chat-project-id', () => null)

  const { createConversation, sendMessage: apiSendMessage, pollRun, getConversationAssets, getMessages } = useApi()

  const isSending = ref(false)

  /** 加载会话的消息历史（页面进入时调用） */
  const loadHistory = async (convId: number) => {
    try {
      const remoteMessages = await getMessages(convId)
      if (remoteMessages?.length) {
        messages.value = remoteMessages.map((m: any) => ({
          id: m.id,
          role: m.role,
          content: m.role === 'user' ? m.content : undefined,
          text: m.role === 'assistant' ? m.text : undefined,
          status: m.status,
          errorMsg: m.errorMsg,
          runId: m.runId,
          mediaItems: [], // 后面按需加载
        }))
        // 加载已完成消息的媒体
        for (const msg of messages.value) {
          if (msg.role === 'assistant' && msg.status === 'completed' && msg.mediaItems?.length === 0) {
            try {
              const assets = await getConversationAssets(convId)
              msg.mediaItems = assets
            } catch { /* 静默 */ }
          }
        }
      }
    } catch (e) {
      console.warn('加载消息历史失败', e)
    }
  }

  /** 发送消息 */
  const sendMessage = async (text: string, ratio: string, model: string) => {
    if (!text || isSending.value) return null

    isSending.value = true
    const fullText = ratio ? `${text}\n[画面比例: ${ratio}]` : text

    // 用户消息
    messages.value.push({
      id: `user-${Date.now()}`,
      role: 'user',
      content: text,
      status: 'completed',
    })

    // Agent 占位消息
    const agentMsg: Message = {
      id: `agent-${Date.now()}`,
      role: 'assistant',
      text: '准备中…',
      status: 'processing',
    }
    messages.value.push(agentMsg)

    try {
      // 确保有会话
      if (!conversationId.value && activeProjectId.value) {
        const conv = await createConversation(activeProjectId.value!, model)
        conversationId.value = conv.id
      }

      const result = await apiSendMessage(conversationId.value!, fullText)
      agentMsg.runId = result.runId

      // 轮询
      await pollUntilComplete(agentMsg)
    } catch (e: any) {
      agentMsg.status = 'failed'
      agentMsg.errorMsg = e?.data?.message || e?.message || '发送失败'
    } finally {
      isSending.value = false
    }

    return agentMsg
  }

  /** 轮询直到完成 */
  const pollUntilComplete = (agentMsg: Message) => {
    return new Promise<void>((resolve) => {
      const timer = setInterval(async () => {
        if (!conversationId.value || !agentMsg.runId) {
          clearInterval(timer)
          resolve()
          return
        }
        try {
          const result = await pollRun(conversationId.value!, agentMsg.runId!)
          if (result.status === 'completed') {
            clearInterval(timer)
            const assets = await getConversationAssets(conversationId.value!)
            agentMsg.status = 'completed'
            agentMsg.text = '生成完成 ✨'
            agentMsg.mediaItems = assets.slice(-(result.mediaCount || 0))
            resolve()
          } else if (result.status === 'failed') {
            clearInterval(timer)
            agentMsg.status = 'failed'
            agentMsg.errorMsg = result.errorMsg
            resolve()
          }
        } catch {
          // 重试
        }
      }, 3000)
    })
  }

  /** 重置聊天（用于完成后归档或登出清理） */
  const reset = () => {
    messages.value = []
    conversationId.value = null
  }

  /** 归档当前会话：标记完成，返回会话信息供侧栏刷新 */
  const archiveCurrentChat = () => {
    const convId = conversationId.value
    const msgs = [...messages.value]
    // 清空当前状态，让创作区回到空状态
    messages.value = []
    conversationId.value = null
    return { conversationId: convId, messages: msgs }
  }

  return {
    messages,
    conversationId,
    activeProjectId,
    isSending,
    loadHistory,
    sendMessage,
    reset,
    archiveCurrentChat,
  }
}