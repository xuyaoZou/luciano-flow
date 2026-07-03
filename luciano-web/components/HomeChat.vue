<!-- 创作视图 — 空白起点，发送后跳转到会话 -->
<template>
  <div class="flex flex-col h-full">
    <!-- 空状态 — 居中大聊天框 -->
    <div class="flex flex-col items-center justify-center h-full animate-fade-in">
      <h1 class="text-2xl font-bold tracking-tight mb-2">创作从这里开始</h1>
      <p class="text-sm text-luciano-muted mb-8">描述你的想法，AI 帮你生成角色、场景、视频</p>

      <!-- 输入框（空状态居中版） -->
      <div class="w-full max-w-2xl">
        <ChatInput
          v-model="inputText"
          :sending="isSending"
          placeholder="描述你想创作的内容，比如：生成一个都市爱情短剧的女主角…"
          @send="handleSend"
          @upload="handleUpload"
        >
          <!-- 快捷选项 -->
          <template #options>
            <div class="flex items-center gap-2 mt-3 flex-wrap">
              <QuickChip label="9:16" :active="ratio === '9:16'" @click="ratio = ratio === '9:16' ? '' : '9:16'" />
              <QuickChip label="16:9" :active="ratio === '16:9'" @click="ratio = ratio === '16:9' ? '' : '16:9'" />
              <QuickChip label="1:1" :active="ratio === '1:1'" @click="ratio = ratio === '1:1' ? '' : '1:1'" />
              <ModelSelector v-model="selectedModel" />
            </div>
          </template>
        </ChatInput>
      </div>

      <!-- 灵感内容 -->
      <div class="w-full max-w-3xl mt-12">
        <p class="text-xs text-luciano-muted/60 uppercase tracking-wider font-semibold mb-4">灵感</p>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
          <div
            v-for="item in inspiration"
            :key="item.title"
            class="group cursor-pointer"
            @click="useInspiration(item)"
          >
            <div class="relative aspect-[3/4] rounded-xl overflow-hidden bg-luciano-card border border-luciano-border/20 group-hover:border-luciano-border transition-all">
              <div v-if="item.thumbnail" class="w-full h-full">
                <img :src="item.thumbnail" :alt="item.title" class="w-full h-full object-cover" loading="lazy" referrerpolicy="no-referrer" />
              </div>
              <div v-else class="w-full h-full flex items-center justify-center text-2xl opacity-40 group-hover:opacity-60 transition-opacity">
                {{ item.emoji }}
              </div>
              <!-- 遮罩 -->
              <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
              <div class="absolute bottom-0 left-0 right-0 p-2.5 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                <p class="text-xs font-medium text-white truncate">{{ item.title }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  sent: [conversationId: number, projectId: number]
}>()

const props = defineProps<{
  requireAuth?: boolean
}>()

const { createProject, listProjects, createConversation, sendMessage, pollRun } = useApi()
const auth = useAuth()

const inputText = ref('')
const isSending = ref(false)
const ratio = ref('9:16')
const selectedModel = ref('xyq')

const inspiration = [
  { title: '都市爱情', emoji: '💕', thumbnail: '' },
  { title: '古风仙侠', emoji: '⚔️', thumbnail: '' },
  { title: '悬疑推理', emoji: '🔍', thumbnail: '' },
  { title: '校园青春', emoji: '🎓', thumbnail: '' },
  { title: '职场逆袭', emoji: '💼', thumbnail: '' },
  { title: '甜蜜日常', emoji: '🍰', thumbnail: '' },
  { title: '末日求生', emoji: '🏚️', thumbnail: '' },
  { title: '奇幻冒险', emoji: '🐉', thumbnail: '' },
]

const useInspiration = (item: any) => {
  inputText.value = `帮我创作一个${item.title}风格的短剧主角形象`
}

/** 确保有活跃项目 */
const ensureProject = async (): Promise<number> => {
  const userId = auth.user.value?.id
  if (userId) {
    const projects = await listProjects(userId)
    if (projects?.length > 0) return projects[0].id
  }
  const project = await createProject({
    title: '我的创作',
    type: 'short_drama',
    description: '自动创建的默认项目',
  })
  return project.id
}

/** 发送 → 创建会话 → 发消息 → 跳转 */
const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || isSending.value) return

  // 未登录拦截
  if (props.requireAuth) {
    navigateTo('/login')
    return
  }

  isSending.value = true
  inputText.value = ''

  try {
    const projectId = await ensureProject()
    const conv = await createConversation(projectId, selectedModel.value)
    const fullText = ratio.value ? `${text}\n[画面比例: ${ratio.value}]` : text

    // 发消息（后端会自动设置 title）
    await sendMessage(conv.id, fullText)

    // 通知父组件跳转到会话视图
    emit('sent', conv.id, projectId)
  } catch (e: any) {
    console.error('[HomeChat] 发送失败', e)
  } finally {
    isSending.value = false
  }
}

const handleUpload = (file: File) => { console.log('upload', file.name) }
</script>