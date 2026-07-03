<!-- 项目视图 — 项目列表 + 项目详情 + 专业模式入口 -->
<template>
  <div class="flex flex-col h-full relative">
    <!-- 未登录遮罩 -->
    <div v-if="requireAuth" class="absolute inset-0 z-10 bg-luciano-bg/80 backdrop-blur-sm flex flex-col items-center justify-center rounded-xl">
      <div class="text-center">
        <div class="text-4xl mb-3">🔒</div>
        <p class="text-sm text-luciano-muted mb-4">登录后查看项目</p>
        <button @click="navigateTo('/login')" class="px-5 py-2 bg-apple-blue text-white text-sm rounded-xl hover:bg-apple-blue/90 transition-colors">去登录</button>
      </div>
    </div>
    <!-- 无项目选中 → 显示项目列表 -->
    <div v-if="!projectId" class="flex flex-col h-full overflow-y-auto animate-fade-in">
      <div class="px-6 pt-6 pb-4 flex items-center justify-between">
        <h1 class="text-xl font-semibold">项目</h1>
        <button
          @click="showNewProjectDialog = true"
          class="px-4 py-2 text-xs bg-apple-blue text-white rounded-full hover:bg-apple-blue/90 active:scale-95 transition-all"
        >
          + 新项目
        </button>
      </div>

      <div v-if="projects.length === 0" class="flex-1 flex flex-col items-center justify-center">
        <div class="text-4xl mb-4">📂</div>
        <h2 class="text-lg font-semibold mb-2">还没有项目</h2>
        <p class="text-sm text-luciano-muted mb-4">在创作页开始对话，归档后自动创建项目</p>
        <button
          @click="$emit('navigate', 'chat')"
          class="px-5 py-2.5 bg-apple-blue text-white text-sm font-medium rounded-full hover:bg-apple-blue/90 active:scale-95 transition-all"
        >
          去创作
        </button>
      </div>

      <div v-else class="px-6 pb-6 space-y-2">
        <div
          v-for="proj in projects"
          :key="proj.id"
          @click="$emit('selectProject', proj)"
          class="flex items-center justify-between bg-luciano-card rounded-xl px-4 py-3 border border-luciano-border/20 hover:border-apple-blue/40 cursor-pointer transition-colors"
        >
          <div>
            <div class="text-sm font-medium">{{ proj.title }}</div>
            <div class="text-[10px] text-luciano-muted mt-0.5">
              {{ proj.type === 'short_drama' ? '短剧' : proj.type || '' }}
              <span v-if="proj.createdAt"> · {{ formatDate(proj.createdAt) }}</span>
            </div>
          </div>
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-luciano-muted"><path d="m9 18 6-6-6-6"/></svg>
        </div>
      </div>
    </div>

    <!-- 有项目 → 详情 -->
    <div v-else class="flex flex-col h-full overflow-y-auto">
      <!-- 项目头部 -->
      <div class="px-6 pt-6 pb-4">
        <div class="flex items-center justify-between">
          <div>
            <button @click="$emit('navigate', 'project')" class="text-xs text-luciano-muted hover:text-apple-blue transition-colors mb-1 flex items-center gap-1">
              <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
              项目列表
            </button>
            <h1 class="text-xl font-semibold">{{ project?.title || '加载中…' }}</h1>
            <p class="text-xs text-luciano-muted mt-1">
              {{ project?.type === 'short_drama' ? '短剧' : project?.type || '' }}
              <span v-if="project?.status"> · {{ project.status }}</span>
              <span v-if="project?.createdAt"> · {{ formatDate(project.createdAt) }}</span>
            </p>
          </div>
          <button
            @click="showNewProjectDialog = true"
            class="px-4 py-2 text-xs bg-apple-blue text-white rounded-full hover:bg-apple-blue/90 active:scale-95 transition-all"
          >
            + 新项目
          </button>
        </div>
      </div>

      <!-- 专业模式入口 -->
      <div class="px-6 pb-4">
        <div class="bg-luciano-card rounded-2xl border border-luciano-border/30 overflow-hidden">
          <div class="px-4 py-3 border-b border-luciano-border/20 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-sm">🎨</span>
              <span class="text-sm font-medium">专业模式</span>
            </div>
            <span class="text-[10px] text-luciano-muted">精细控制每个创作环节</span>
          </div>
          <div class="grid grid-cols-2 sm:grid-cols-4 divide-x divide-luciano-border/20">
            <NuxtLink
              v-for="item in professionalItems"
              :key="item.key"
              :to="item.to"
              class="flex flex-col items-center gap-1.5 py-4 hover:bg-luciano-border/10 transition-colors"
            >
              <span class="text-xl">{{ item.icon }}</span>
              <span class="text-xs text-luciano-muted">{{ item.label }}</span>
            </NuxtLink>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建项目弹窗 -->
    <Teleport to="body">
      <div v-if="showNewProjectDialog" class="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-center justify-center" @click.self="showNewProjectDialog = false">
        <div class="bg-luciano-card rounded-2xl p-6 w-full max-w-sm border border-luciano-border/50 shadow-2xl">
          <h3 class="text-lg font-semibold mb-4">新建项目</h3>
          <div class="space-y-3">
            <div>
              <label class="text-xs text-luciano-muted block mb-1">项目名称</label>
              <input v-model="newProjectTitle" class="w-full bg-luciano-bg border border-luciano-border/50 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-apple-blue transition-colors" placeholder="我的短剧" />
            </div>
            <div>
              <label class="text-xs text-luciano-muted block mb-1">类型</label>
              <select v-model="newProjectType" class="w-full bg-luciano-bg border border-luciano-border/50 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-apple-blue transition-colors">
                <option value="short_drama">短剧</option>
                <option value="comic">漫剧</option>
                <option value="video">视频</option>
              </select>
            </div>
          </div>
          <div class="flex gap-3 mt-6">
            <button @click="showNewProjectDialog = false" class="flex-1 px-4 py-2 text-sm rounded-lg border border-luciano-border/50 hover:bg-luciano-border/20 transition-colors">取消</button>
            <button @click="handleCreateProject" class="flex-1 px-4 py-2 text-sm rounded-lg bg-apple-blue text-white hover:bg-apple-blue/90 active:scale-95 transition-all">创建</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
interface Props {
  projectId?: number | null
  requireAuth?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  navigate: [view: string, projectId?: number]
  newProject: [project: any]
  selectProject: [project: any]
}>()

const { fetchProject, listProjects, createProject } = useApi()
const auth = useAuth()

const project = ref<any>(null)
const projects = ref<any[]>([])
const showNewProjectDialog = ref(false)
const newProjectTitle = ref('')
const newProjectType = ref('short_drama')

const professionalItems = computed(() => {
  const pid = props.projectId || 0
  return [
    { key: 'characters', icon: '👤', label: '角色', to: `/projects/${pid}/characters` },
    { key: 'scenes', icon: '🏞️', label: '场景', to: `/projects/${pid}/scenes` },
    { key: 'props', icon: '📦', label: '道具', to: `/projects/${pid}/props` },
    { key: 'storyboards', icon: '🎬', label: '分镜', to: `/projects/${pid}/storyboards` },
  ]
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

const loadProjects = async () => {
  const userId = auth.user.value?.id
  if (!userId) return
  try {
    const list = await listProjects(userId)
    if (list && Array.isArray(list)) {
      projects.value = list
    }
  } catch { /* 静默 */ }
}

const handleCreateProject = async () => {
  if (!newProjectTitle.value.trim()) return
  const proj = await createProject({
    title: newProjectTitle.value.trim(),
    type: newProjectType.value,
  })
  showNewProjectDialog.value = false
  newProjectTitle.value = ''
  await loadProjects()
  emit('newProject', proj)
  emit('selectProject', proj)
}

watch(() => props.projectId, async (id) => {
  if (!id) {
    // 无项目选中，加载项目列表
    await loadProjects()
    return
  }
  try {
    project.value = await fetchProject(id)
  } catch { /* 静默 */ }
}, { immediate: true })
</script>