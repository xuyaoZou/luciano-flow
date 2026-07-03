<!-- Agent 对话页 — iMessage 风格，对话即创作 -->
<template>
  <div class="flex flex-col h-full">
    <!-- 消息流 -->
    <div ref="scrollContainer" class="flex-1 overflow-y-auto px-6 py-6 space-y-4">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="flex flex-col items-center justify-center h-full text-center animate-fade-in">
        <div class="text-5xl mb-4">🎬</div>
        <h2 class="text-xl font-semibold mb-2">描述你的创意</h2>
        <p class="text-luciano-muted text-sm max-w-xs">我来帮你生成角色、场景、分镜……</p>
      </div>

      <!-- 消息列表 -->
      <div v-for="msg in messages" :key="msg.id" class="animate-slide-up">
        <!-- 用户消息 -->
        <div v-if="msg.role === 'user'" class="flex justify-end">
          <div class="bg-apple-blue text-white px-4 py-2.5 rounded-2xl rounded-br-md max-w-md text-sm leading-relaxed">
            {{ msg.content }}
          </div>
        </div>

        <!-- Agent 回复 -->
        <div v-else class="flex justify-start gap-3">
          <div class="w-8 h-8 rounded-full bg-luciano-card border border-luciano-border flex items-center justify-center text-xs shrink-0">
            🤖
          </div>
          <div class="space-y-2 max-w-lg">
            <!-- 文字回复 -->
            <div v-if="msg.text" class="bg-luciano-card px-4 py-2.5 rounded-2xl rounded-bl-md text-sm leading-relaxed">
              {{ msg.text }}
            </div>

            <!-- 媒体卡片 -->
            <div v-for="media in msg.mediaItems" :key="media.id" class="group">
              <div class="bg-luciano-card rounded-2xl overflow-hidden border border-luciano-border/50 hover:border-luciano-border transition-colors">
                <!-- 图片 -->
                <div v-if="media.mediaType === 'image'" class="relative">
                  <img
                    :src="media.url"
                    :alt="media.name || '生成的图片'"
                    class="w-full object-contain max-h-80"
                    loading="lazy"
                    @load="$event.target.classList.add('loaded')"
                  />
                  <!-- 来源标签 -->
                  <span class="absolute top-2 left-2 text-[10px] bg-black/60 backdrop-blur-md text-white px-2 py-0.5 rounded-full">
                    AI 生成
                  </span>
                </div>
                <!-- 底部操作 -->
                <div class="px-3 py-2 flex items-center justify-between">
                  <span class="text-xs text-luciano-muted">{{ media.mediaType === 'image' ? '图片' : '视频' }}</span>
                  <button
                    @click="linkMedia(media)"
                    class="text-xs text-apple-blue hover:text-apple-blue/80 font-medium"
                  >
                    关联到项目 →
                  </button>
                </div>
              </div>
            </div>

            <!-- 生成中 -->
            <div v-if="msg.status === 'processing'" class="flex items-center gap-2 text-luciano-muted text-sm">
              <div class="flex gap-1">
                <span class="w-1.5 h-1.5 rounded-full bg-apple-blue animate-breathe" style="animation-delay: 0s" />
                <span class="w-1.5 h-1.5 rounded-full bg-apple-blue animate-breathe" style="animation-delay: 0.4s" />
                <span class="w-1.5 h-1.5 rounded-full bg-apple-blue animate-breathe" style="animation-delay: 0.8s" />
              </div>
              生成中…
            </div>

            <!-- 错误 -->
            <div v-if="msg.status === 'failed'" class="text-xs text-apple-red">
              {{ msg.errorMsg || '生成失败' }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="border-t border-luciano-border/30 px-6 py-4">
      <form @submit.prevent="sendMessage" class="flex items-end gap-3">
        <div class="flex-1 relative">
          <textarea
            v-model="inputText"
            ref="inputRef"
            placeholder="描述你想生成的内容…"
            rows="1"
            class="w-full bg-luciano-card border border-luciano-border/50 rounded-2xl px-4 py-3 text-sm resize-none focus:outline-none focus:border-apple-blue/50 transition-colors placeholder:text-luciano-muted/60"
            @keydown.enter.exact.prevent="sendMessage"
            @input="autoResize"
          />
        </div>
        <button
          type="submit"
          :disabled="!inputText.trim() || isSending"
          class="shrink-0 w-10 h-10 rounded-full bg-apple-blue text-white flex items-center justify-center disabled:opacity-30 disabled:cursor-not-allowed hover:bg-apple-blue/90 active:scale-95 transition-all"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3.478 2.404a.75.75 0 00-.926.941l2.432 7.905H13.5a.75.75 0 010 1.5H5.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.404z"/>
          </svg>
        </button>
      </form>
    </div>

    <!-- 关联浮层 -->
    <LinkPopover
      v-if="linkTarget"
      :media="linkTarget"
      :project-id="projectId"
      @close="linkTarget = null"
      @linked="onLinked"
    />
  </div>
</template>

<script setup lang="ts">
interface Props {
  projectId: number
  project?: any
}

const props = defineProps<Props>()
const { createConversation, sendMessage: apiSendMessage, pollRun, getConversationAssets } = useApi()

const inputText = ref('')
const isSending = ref(false)
const scrollContainer = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()
const linkTarget = ref<any>(null)

interface Message {
  id: string
  role: 'user' | 'assistant'
  content?: string
  text?: string
  mediaItems?: any[]
  status?: 'processing' | 'completed' | 'failed'
  errorMsg?: string
  runId?: string
}

const messages = ref<Message[]>([])
let conversationId: number | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isSending.value) return

  inputText.value = ''
  isSending.value = true

  // 用户消息
  messages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: text,
  })
  scrollToBottom()

  // Agent 占位消息
  const agentMsg: Message = {
    id: `agent-${Date.now()}`,
    role: 'assistant',
    text: '收到，正在生成…',
    status: 'processing',
  }
  messages.value.push(agentMsg)
  scrollToBottom()

  try {
    // 创建/复用会话
    if (!conversationId) {
      const conv = await createConversation(props.projectId)
      conversationId = conv.id
    }

    // 发送消息
    const result = await apiSendMessage(conversationId, text)
    agentMsg.runId = result.runId

    // 开始轮询
    startPolling(agentMsg)
  } catch (e: any) {
    agentMsg.status = 'failed'
    agentMsg.errorMsg = e.message || '发送失败'
  } finally {
    isSending.value = false
  }
}

const startPolling = (agentMsg: Message) => {
  if (pollTimer) clearInterval(pollTimer)

  pollTimer = setInterval(async () => {
    if (!conversationId || !agentMsg.runId) return

    try {
      const result = await pollRun(conversationId, agentMsg.runId)

      if (result.status === 'completed') {
        clearInterval(pollTimer!)
        pollTimer = null

        // 获取媒体
        const assets = await getConversationAssets(conversationId)
        agentMsg.status = 'completed'
        agentMsg.text = '生成完成 ✨'
        agentMsg.mediaItems = assets.slice(-result.mediaCount)
        scrollToBottom()
      } else if (result.status === 'failed') {
        clearInterval(pollTimer!)
        pollTimer = null
        agentMsg.status = 'failed'
        agentMsg.errorMsg = result.errorMsg
      }
    } catch (e: any) {
      // 轮询错误不中断，继续重试
      console.warn('Poll error:', e)
    }
  }, 3000)
}

const linkMedia = (media: any) => {
  linkTarget.value = media
}

const onLinked = () => {
  linkTarget.value = null
  // 可选：在消息卡片上标记已关联
}

const autoResize = () => {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    inputRef.value.style.height = inputRef.value.scrollHeight + 'px'
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (scrollContainer.value) {
      scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
    }
  })
}

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>