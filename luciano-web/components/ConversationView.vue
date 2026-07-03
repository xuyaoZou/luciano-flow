<!-- 会话视图 - 每条 Agent 回复独立气泡,突出思考过程 -->
<template>
  <div class="flex flex-col h-full">
    <!-- 会话头部 -->
    <div class="px-6 py-3 border-b border-luciano-border/20 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-3">
        <button @click="$emit('back')" class="w-7 h-7 rounded-lg hover:bg-luciano-border/20 flex items-center justify-center text-luciano-muted transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
        </button>
        <div>
          <h2 class="text-sm font-medium truncate max-w-xs">{{ convTitle || '会话' }}</h2>
          <p class="text-[10px] text-luciano-muted">
            {{ convProvider === 'xyq' ? '小云雀' : convProvider }} · {{ formatDate(convCreatedAt) }}
          </p>
        </div>
      </div>

    </div>

    <!-- 消息区 -->
    <div ref="scrollContainer" class="flex-1 overflow-y-auto px-6 py-4">
      <div v-if="messages.length === 0" class="flex items-center justify-center h-full text-sm text-luciano-muted">
        加载中...
      </div>
      <div v-else class="space-y-3">
        <div v-for="(msg, idx) in messages" :key="msg.id" class="animate-slide-up">

          <!-- 用户消息 -->
          <div v-if="msg.type === 'user'" class="flex justify-end">
            <div class="bg-apple-blue text-white px-4 py-2.5 rounded-2xl rounded-br-md max-w-md text-sm leading-relaxed">{{ msg.content }}</div>
          </div>

          <!-- Agent 思考/分析文字 -->
          <div v-else-if="msg.type === 'agent-text'" class="flex justify-start gap-3">
            <div class="w-7 h-7 rounded-full bg-luciano-card border border-luciano-border flex items-center justify-center text-[10px] shrink-0 mt-1">🦞</div>
            <div class="max-w-lg">
              <div class="bg-luciano-card px-4 py-2.5 rounded-2xl rounded-bl-md text-sm leading-relaxed whitespace-pre-wrap">{{ msg.text }}</div>
            </div>
          </div>

          <!-- Agent 工具状态提示 -->
          <div v-else-if="msg.type === 'agent-status'" class="flex justify-start gap-3">
            <div class="w-7 h-7 shrink-0"></div>
            <div class="px-3 py-1.5 rounded-xl text-xs flex items-center gap-1.5"
              :class="msg.done ? 'text-luciano-muted/60 bg-luciano-card/30' : 'text-luciano-muted bg-luciano-card/50'"
            >
              <span v-if="msg.done" class="text-green-500">✓</span>
              <span v-else class="w-1.5 h-1.5 rounded-full bg-apple-blue animate-pulse" />
              {{ msg.done ? msg.doneText : msg.text }}
            </div>
          </div>

          <!-- Agent 媒体 -->
          <div v-else-if="msg.type === 'agent-media'" class="flex justify-start gap-3">
            <div class="w-7 h-7 shrink-0"></div>
            <div class="max-w-lg w-full">
              <!-- 单媒体：大图/视频显示 -->
              <div v-if="msg.mediaItems.length === 1" class="bg-luciano-card rounded-2xl overflow-hidden border border-luciano-border/50">
                <!-- 图片 -->
                <div v-if="msg.mediaItems[0].mediaType === 'image'" class="cursor-pointer" @click="openLightbox(msg.mediaItems[0])">
                  <img :src="getBlobUrl(msg.mediaItems[0].id, msg.mediaItems[0].url)" class="w-full rounded-2xl" style="max-height: 400px; object-fit: contain;" loading="lazy" />
                </div>
                <!-- 视频 -->
                <div v-else-if="msg.mediaItems[0].mediaType === 'video'" class="cursor-pointer" @click="openLightbox(msg.mediaItems[0])">
                  <video :src="getVideoUrl(msg.mediaItems[0])" class="w-full rounded-2xl" style="max-height: 400px;" controls preload="metadata" :poster="msg.mediaItems[0].thumbnailUrl || undefined" />
                </div>
                <div class="px-3 py-1.5 flex items-center justify-between">
                  <span class="text-xs text-luciano-muted">{{ msg.mediaItems[0].mediaType === 'image' ? '图片' : '视频' }}</span>
                  <button @click="linkMedia(msg.mediaItems[0])" class="text-xs text-apple-blue hover:text-apple-blue/80 font-medium">关联到项目 →</button>
                </div>
              </div>
              <!-- 多媒体：网格排列 -->
              <div v-else :class="[
                msg.mediaItems.length === 2 ? 'grid grid-cols-2 gap-2' :
                msg.mediaItems.length === 3 ? 'grid grid-cols-3 gap-2' :
                msg.mediaItems.length === 4 ? 'grid grid-cols-2 gap-2' :
                'grid grid-cols-3 gap-2'
              ]">
                <div v-for="media in msg.mediaItems" :key="media.id" class="rounded-xl overflow-hidden bg-luciano-card border border-luciano-border/30">
                  <!-- 图片 -->
                  <div v-if="media.mediaType === 'image'" class="cursor-pointer" @click="openLightbox(media)">
                    <img :src="getBlobUrl(media.id, media.url)" class="w-full rounded-xl" style="max-height: 280px; object-fit: contain;" loading="lazy" />
                  </div>
                  <!-- 视频 -->
                  <div v-else-if="media.mediaType === 'video'" class="cursor-pointer" @click="openLightbox(media)">
                    <video :src="getVideoUrl(media)" class="w-full rounded-xl" style="max-height: 280px;" preload="metadata" :poster="media.thumbnailUrl || undefined" />
                    <div class="absolute inset-0 flex items-center justify-center pointer-events-none">
                      <div class="w-10 h-10 rounded-full bg-black/50 flex items-center justify-center">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="white" class="ml-0.5"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Agent 连接中/思考中占位 -->
          <div v-else-if="msg.type === 'agent-thinking'" class="flex justify-start gap-3">
            <div class="w-7 h-7 rounded-full bg-luciano-card border border-luciano-border flex items-center justify-center text-[10px] shrink-0 mt-1">🦞</div>
            <div class="flex items-center gap-2 text-luciano-muted text-sm bg-luciano-card px-4 py-2.5 rounded-2xl rounded-bl-md">
              <span class="flex gap-1">
                <span class="w-1.5 h-1.5 rounded-full bg-apple-blue animate-breathe" style="animation-delay:0s" />
                <span class="w-1.5 h-1.5 rounded-full bg-apple-blue animate-breathe" style="animation-delay:0.4s" />
                <span class="w-1.5 h-1.5 rounded-full bg-apple-blue animate-breathe" style="animation-delay:0.8s" />
              </span>
              {{ msg.text || '正在连接 Agent...' }}
            </div>
          </div>

          <!-- 错误 -->
          <div v-else-if="msg.type === 'agent-error'" class="flex justify-start gap-3">
            <div class="w-7 h-7 shrink-0"></div>
            <div class="text-xs text-luciano-red bg-luciano-red/5 px-3 py-2 rounded-xl">{{ msg.text }}</div>
          </div>

          <!-- 段落时间戳：只在组内最后一条显示，对齐气泡方向 -->
          <div v-if="msg.timestamp && isGroupEnd(idx)" class="mt-1"
            :class="getMessageGroup(msg) === 'user' ? 'text-right mr-1' : 'text-left ml-11'"
          >
            <span class="text-[10px] text-luciano-muted/40">{{ formatTime(msg.timestamp) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入框 -->
    <div class="border-t border-luciano-border/20 px-6 py-4">
      <div class="max-w-2xl mx-auto">
        <ChatInput
          v-model="inputText"
          :sending="isSending"
          placeholder="继续对话..."
          compact
          @send="handleSend"
        />
      </div>
    </div>

    <!-- 关联浮层 -->
    <LinkPopover
      v-if="linkTarget"
      :media="linkTarget"
      :project-id="projectId"
      @close="linkTarget = null"
      @linked="onLinked"
    />

    <!-- 大图预览 -->
    <Lightbox
      v-if="lightboxAsset"
      :asset="lightboxAsset"
      :projectId="props.projectId || props.conversationId"
      @close="lightboxAsset = null"
    />
  </div>
</template>

<script setup lang="ts">
interface Props {
  conversationId: number
  projectId?: number | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  back: []
}>()

const { sendMessage, pollRun, getConversationAssets, getMessages } = useApi()
const { loadMedia, loadMediaList, getBlobUrl, cleanup } = useMediaLoader()
const { token: authToken } = useApi()

// 消息列表 - 每条消息独立气泡
// type: 'user' | 'agent-text' | 'agent-status' | 'agent-media' | 'agent-thinking' | 'agent-error'
const messages = ref<any[]>([])
const inputText = ref('')
const isSending = ref(false)
const linkTarget = ref<any>(null)
const lightboxAsset = ref<any>(null)
const scrollContainer = ref<HTMLElement>()

// 会话元信息
const convTitle = ref('')
const convProvider = ref('')
const convStatus = ref('')
const convCreatedAt = ref('')

// 轮询控制
let pollTimer: ReturnType<typeof setInterval> | null = null
// 去重 key 集合
const seenKeys = new Set<string>()
const completedRunIds = new Set<string>()  // 防止同一 runId 的 completed 被重复处理
// 当前轮询的 agent-thinking 占位消息 ID(用于完成后移除)
let thinkingPlaceholderId: string | null = null

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const mo = (d.getMonth() + 1).toString().padStart(2, '0')
  const da = d.getDate().toString().padStart(2, '0')
  const h = d.getHours().toString().padStart(2, '0')
  const mi = d.getMinutes().toString().padStart(2, '0')
  const s = d.getSeconds().toString().padStart(2, '0')
  return `${y}/${mo}/${da} ${h}:${mi}:${s}`
}

/** 判断消息分组：user 独立一组，agent 类型合为一组 */
const getMessageGroup = (msg: any): string => {
  if (msg.type === 'user') return 'user'
  return 'agent'
}

/** 判断 idx 位置的消息是否是当前分组的最后一条 */
const isGroupEnd = (idx: number): boolean => {
  if (idx === messages.value.length - 1) return true
  return getMessageGroup(messages.value[idx + 1]) !== getMessageGroup(messages.value[idx])
}

/** 加载会话历史消息 — 每条 assistant 消息自带 mediaAssets，直接关联 */
const loadMessages = async () => {
  try {
    const remoteMessages = await getMessages(props.conversationId)
    if (!remoteMessages?.length) return

    // 标题：取第一条用户消息
    const firstUserMsg = remoteMessages.find((m: any) => m.role === 'user')
    if (firstUserMsg) {
      const content = firstUserMsg.content || ''
      convTitle.value = content.length > 50 ? content.substring(0, 50) + '…' : content
    }

    const allImageAssets: any[] = []  // 收集所有图片用于预加载 blob URL

    for (const m of remoteMessages) {
      if (m.role === 'user') {
        messages.value.push({
          id: `db-${m.id}`,
          type: 'user',
          content: m.content,
          timestamp: m.createdAt,
        })
      } else if (m.role === 'assistant') {
        // 将 text 按段落拆分成独立气泡
        if (m.text) {
          const paragraphs = m.text
            .split(/\n{2,}/)
            .map((p: string) => p.trim())
            .filter((p: string) => p.length > 0)

          for (const para of paragraphs) {
            messages.value.push({
              id: `db-${m.id}-${messages.value.length}`,
              type: 'agent-text',
              text: para,
              timestamp: m.createdAt,
            })
          }
        }

        // 直接用该消息关联的 mediaAssets，不再靠切片推算
        const mediaAssets = m.mediaAssets || []
        const mediaToShow = mediaAssets.filter((a: any) => a.mediaType === 'image' || a.mediaType === 'video')
        if (mediaToShow.length > 0) {
          const imageAssets = mediaToShow.filter((a: any) => a.mediaType === 'image')
          allImageAssets.push(...imageAssets)
          messages.value.push({
            id: `db-media-${m.id}`,
            type: 'agent-media',
            mediaItems: mediaToShow,
            timestamp: m.createdAt,
          })
        }
      }
    }

    // 预加载所有图片 blob URL
    if (allImageAssets.length) {
      await loadMediaList(allImageAssets)
    }

    scrollToBottom()

    // 如果最后一条 assistant 消息是 processing 状态，自动启动轮询
    const lastAssistant = [...remoteMessages].reverse().find((m: any) => m.role === 'assistant')
    if (lastAssistant?.status === 'processing' && lastAssistant.runId) {
      isSending.value = true
      thinkingPlaceholderId = `thinking-${Date.now()}`
      messages.value.push({
        id: thinkingPlaceholderId,
        type: 'agent-thinking',
        text: '正在连接 Agent…',
      })
      scrollToBottom()
      startPolling(lastAssistant.runId)
    }
  } catch (e) {
    console.error('[ConversationView] 加载消息失败', e)
  }
}

/** 发送消息 */
const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || isSending.value) return

  inputText.value = ''
  isSending.value = true
  seenKeys.clear()  // 每次发消息重置去重

  // 用户消息气泡
  messages.value.push({
    id: `user-${Date.now()}`,
    type: 'user',
    content: text,
    timestamp: new Date().toISOString(),
  })

  // 思考中占位
  thinkingPlaceholderId = `thinking-${Date.now()}`
  messages.value.push({
    id: thinkingPlaceholderId,
    type: 'agent-thinking',
    text: '正在连接 Agent...',
  })
  scrollToBottom()

  try {
    const result = await sendMessage(props.conversationId, text)
    // 开始轮询,实时更新文字消息
    startPolling(result.runId)
  } catch (e: any) {
    // 移除思考中占位
    removeThinkingPlaceholder()
    messages.value.push({
      id: `error-${Date.now()}`,
      type: 'agent-error',
      text: e?.data?.message || e?.message || '发送失败',
    })
    isSending.value = false
  }
}

/** 移除思考中占位 */
const removeThinkingPlaceholder = () => {
  if (thinkingPlaceholderId) {
    const idx = messages.value.findIndex(m => m.id === thinkingPlaceholderId)
    if (idx >= 0) messages.value.splice(idx, 1)
    thinkingPlaceholderId = null
  }
}

/** 轮询 Agent 结果,每条文字消息独立气泡 */
const startPolling = (runId: string) => {
  const poll = async () => {
    try {
      const result = await pollRun(props.conversationId, runId)

      if (result.textMessages?.length) {
        // 收到任何文字消息就移除"思考中"占位
        removeThinkingPlaceholder()

        for (const tm of result.textMessages) {
          const dedupeKey = `${tm.entryIndex}:${tm.subType}:${(tm.text || '').substring(0, 30)}`
          if (seenKeys.has(dedupeKey)) continue
          seenKeys.add(dedupeKey)

          if (tm.subType === 'tool_call_req') {
            // 工具调用提示 → 独立状态气泡
            if (tm.text) {
              messages.value.push({
                id: `status-${tm.entryIndex}-${dedupeKey.length}`,
                type: 'agent-status',
                text: tm.text,
              })
            }
          } else if (tm.subType === 'biz/x_artifact_slots_part') {
            // 图片生成占位 → 状态气泡
            messages.value.push({
              id: `slots-${tm.entryIndex}`,
              type: 'agent-status',
              text: '正在生成图片...',
            })
          } else if (tm.text) {
            // 纯文字消息 → 独立文字气泡
            messages.value.push({
              id: `text-${tm.entryIndex}-${dedupeKey.length}`,
              type: 'agent-text',
              text: tm.text,
            })
          }
        }
        scrollToBottom()
      }

      // 完成
      if (result.status === 'completed') {
        // 防止同一 runId 的 completed 被重复处理（轮询竞态）
        if (completedRunIds.has(runId)) {
          stopPolling()
          isSending.value = false
          return
        }
        completedRunIds.add(runId)

        removeThinkingPlaceholder()

        // 所有状态气泡标记为完成
        for (const m of messages.value) {
          if (m.type === 'agent-status' && !m.done) {
            m.done = true
            m.doneText = m.text.replace('正在', '已').replace('...', '')
          }
        }

        // 完成：用 runId 查询本次 run 的 media_assets
        const runAssets = await getConversationAssets(props.conversationId, runId)
        const mediaToShow = (runAssets || []).filter((a: any) => a.mediaType === 'image' || a.mediaType === 'video')
        if (mediaToShow.length) {
          // 只预加载图片的 blob URL，视频直接用原始 URL
          const imageAssets = mediaToShow.filter((a: any) => a.mediaType === 'image')
          if (imageAssets.length) await loadMediaList(imageAssets)
          messages.value.push({
            id: `media-${Date.now()}`,
            type: 'agent-media',
            mediaItems: mediaToShow,
            timestamp: new Date().toISOString(),
          })
        }
        stopPolling()
        isSending.value = false
        scrollToBottom()
      } else if (result.status === 'failed') {
        removeThinkingPlaceholder()
        messages.value.push({
          id: `error-${Date.now()}`,
          type: 'agent-error',
          text: result.errorMsg || '生成失败',
        })
        stopPolling()
        isSending.value = false
      }
    } catch {
      // 轮询失败,继续重试
    }
  }

  // 立即轮询一次,然后每 3 秒
  poll()
  pollTimer = setInterval(poll, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const linkMedia = (media: any) => { linkTarget.value = media }
const openLightbox = (media: any) => { lightboxAsset.value = media }
const onLinked = () => { linkTarget.value = null }

/** 视频使用落地文件 URL + token 参数 */
const getVideoUrl = (media: any) => {
  if (!media?.id) return media?.url || ''
  const t = authToken.value || ''
  const base = useRuntimeConfig().public.apiBase || '/api/v1'
  return `${base}/media/${media.id}/file${t ? '?token=' + t : ''}`
}

const scrollToBottom = () => {
  nextTick(() => {
    if (scrollContainer.value) scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
  })
}

watch(() => props.conversationId, (id) => {
  stopPolling()
  messages.value = []
  seenKeys.clear()
  if (id) loadMessages()
}, { immediate: true })

onUnmounted(() => {
  stopPolling()
  cleanup()
})
</script>