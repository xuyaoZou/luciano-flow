<!-- 主布局 — 左边栏 + 主内容区 -->
<template>
  <div class="flex h-screen overflow-hidden bg-luciano-bg text-luciano-text">
    <!-- 左边栏 -->
    <Sidebar
      :collapsed="sidebarCollapsed"
      :active-view="sidebarActiveView"
      :active-conversation-id="activeConversationId"
      :conversations="conversations"
      @toggle="sidebarCollapsed = !sidebarCollapsed"
      @navigate="handleNavigate"
      @open-conversation="handleOpenConversation"
    />

    <!-- 主内容区 -->
    <main class="flex-1 flex flex-col overflow-hidden">
      <!-- 右上角用户信息 -->
      <header class="flex items-center justify-between px-6 py-3 shrink-0">
        <div class="flex items-center gap-2">
          <!-- 移动端展开侧栏 -->
          <button
            v-if="sidebarCollapsed"
            @click="sidebarCollapsed = false"
            class="w-8 h-8 rounded-lg hover:bg-luciano-card flex items-center justify-center text-luciano-muted transition-colors"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" x2="20" y1="12" y2="12"/><line x1="4" x2="20" y1="6" y2="6"/><line x1="4" x2="20" y1="18" y2="18"/></svg>
          </button>
        </div>

        <div class="flex items-center gap-4">
          <!-- 主题切换 -->
          <button @click="theme.toggleTheme()" class="w-8 h-8 rounded-lg hover:bg-luciano-card flex items-center justify-center text-luciano-muted transition-all hover:rotate-12" :title="theme.isDark.value ? '切换亮色模式' : '切换暗色模式'">
            <!-- 太阳图标（深色模式下显示） -->
            <svg v-if="theme.isDark.value" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" x2="12" y1="1" y2="3"/><line x1="12" x2="12" y1="21" y2="23"/><line x1="4.22" x2="5.64" y1="4.22" y2="5.64"/><line x1="18.36" x2="19.78" y1="18.36" y2="19.78"/><line x1="1" x2="3" y1="12" y2="12"/><line x1="21" x2="23" y1="12" y2="12"/><line x1="4.22" x2="5.64" y1="19.78" y2="18.36"/><line x1="18.36" x2="19.78" y1="5.64" y2="4.22"/></svg>
            <!-- 月亮图标（亮色模式下显示） -->
            <svg v-else xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
          </button>
          <div v-if="auth.isLoggedIn.value && auth.user.value?.credits != null" class="flex items-center gap-1.5 text-xs text-luciano-muted bg-luciano-card px-3 py-1.5 rounded-full">
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
            {{ auth.user.value.credits }} 积分
          </div>
          <div ref="menuRef" class="relative">
            <button @click="showUserMenu = !showUserMenu" class="flex items-center gap-2 hover:opacity-80 transition-opacity">
              <div class="w-7 h-7 rounded-full bg-apple-blue/20 flex items-center justify-center text-xs font-medium text-apple-blue">
                {{ userInitial }}
              </div>
              <span class="text-sm font-medium">{{ auth.isLoggedIn.value ? (auth.user.value?.username || '用户') : '未登录' }}</span>
            </button>

            <!-- 用户菜单 -->
            <Transition name="dropdown">
              <div v-if="showUserMenu" class="absolute right-0 top-full mt-2 w-36 bg-luciano-card border border-luciano-border/50 rounded-xl shadow-xl overflow-hidden z-50">
                <template v-if="auth.isLoggedIn.value">
                  <button @click="handleLogout" class="w-full text-left px-4 py-2.5 text-xs text-luciano-muted hover:bg-luciano-border/20 transition-colors">
                    退出登录
                  </button>
                </template>
                <template v-else>
                  <button @click="goLogin" class="w-full text-left px-4 py-2.5 text-xs text-apple-blue font-medium hover:bg-luciano-border/20 transition-colors">
                    登录
                  </button>
                  <button @click="goRegister" class="w-full text-left px-4 py-2.5 text-xs text-luciano-muted hover:bg-luciano-border/20 transition-colors">
                    注册
                  </button>
                </template>
              </div>
            </Transition>
          </div>
        </div>
      </header>

      <!-- 未登录提示 -->
      <div v-if="!auth.isLoggedIn.value" class="mx-6 mb-2 px-4 py-2.5 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center gap-2 text-sm text-amber-400">
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" x2="3" y1="12" y2="12"/></svg>
        <span>请先登录以使用完整功能</span>
        <button @click="goLogin" class="ml-auto px-3 py-1 text-xs bg-amber-500/20 hover:bg-amber-500/30 rounded-lg transition-colors font-medium">登录</button>
      </div>

      <!-- 内容视图 -->
      <div :key="activeView" class="flex-1 overflow-hidden">
        <!-- 创作：空白输入框，发送后跳转 -->
        <HomeChat
          v-if="activeView === 'chat'"
          :require-auth="!auth.isLoggedIn.value"
          @sent="handleChatSent"
        />
        <!-- 创作工作台 -->
        <CreateWorkspace
          v-else-if="activeView === 'create'"
          :require-auth="!auth.isLoggedIn.value"
        />
        <!-- 会话视图 -->
        <ConversationView
          v-else-if="activeView === 'conversation' && activeConversationId"
          :conversation-id="activeConversationId"
          :project-id="currentProjectId"
          @back="switchView('chat')"
        />
        <!-- 专业模式 -->
        <ProfessionalMode
          v-else-if="activeView === 'professional'"
          :project-id="currentProjectId"
          :require-auth="!auth.isLoggedIn.value"
        />
        <!-- 项目列表 -->
        <ProjectView
          v-else-if="activeView === 'project'"
          :project-id="currentProjectId"
          :require-auth="!auth.isLoggedIn.value"
        />
        <!-- 资产 -->
        <AssetsView
          v-else-if="activeView === 'assets'"
          :project-id="currentProjectId"
          :require-auth="!auth.isLoggedIn.value"
        />
        <!-- 存储配置 -->
        <StorageSettings
          v-else-if="activeView === 'storage'"
        />
      </div>
    </main>
  </div>

  <!-- 点击外部关闭用户菜单：由 mousedown 事件处理 -->
</template>

<script setup lang="ts">
const { listProjects, listConversations } = useApi()
const auth = useAuth()
const theme = useTheme()

const sidebarCollapsed = ref(false)
const activeView = ref<'chat' | 'conversation' | 'professional' | 'create' | 'project' | 'assets' | 'storage'>('chat')
const activeConversationId = ref<number | null>(null)
const currentProjectId = ref<number | null>(null)
const showUserMenu = ref(false)

const userInitial = computed(() => {
  if (auth.isLoggedIn.value) return auth.user.value?.username?.charAt(0)?.toUpperCase() || '?'
  return '?'
})

// 侧边栏高亮
const sidebarActiveView = computed(() => {
  if (activeView.value === 'conversation') return 'chat'
  return activeView.value
})

const conversations = ref<any[]>([])

/** 未登录时导航拦截 */
const handleNavigate = (view: string) => {
  if (!auth.isLoggedIn.value) {
    goLogin()
    return
  }
  activeView.value = view as any
  if (view === 'chat') {
    activeConversationId.value = null
  }
}

const handleOpenConversation = (conv: any) => {
  if (!auth.isLoggedIn.value) {
    goLogin()
    return
  }
  activeConversationId.value = conv.id
  currentProjectId.value = conv.projectId
  activeView.value = 'conversation'
}

const loadConversations = async () => {
  if (!auth.isLoggedIn.value) return
  try {
    const list = await listConversations()
    if (list && Array.isArray(list)) {
      conversations.value = list
        .map((c: any) => ({
          id: c.id, title: c.title, provider: c.provider, status: c.status,
          projectId: c.projectId, createdAt: c.createdAt, updatedAt: c.updatedAt,
        }))
        .sort((a: any, b: any) => new Date(b.updatedAt || b.createdAt).getTime() - new Date(a.updatedAt || a.createdAt).getTime())
    }
  } catch { /* 静默 */ }
}

const handleChatSent = async (conversationId: number, projectId: number) => {
  activeConversationId.value = conversationId
  currentProjectId.value = projectId
  activeView.value = 'conversation'
  await loadConversations()
}

const switchView = (view: string) => {
  if (!auth.isLoggedIn.value && view !== 'chat') {
    goLogin()
    return
  }
  activeView.value = view as any
  if (view === 'chat') activeConversationId.value = null
}

const goLogin = () => {
  showUserMenu.value = false
  navigateTo('/login')
}

const goRegister = () => {
  showUserMenu.value = false
  navigateTo('/login?tab=register')
}

const handleLogout = () => {
  showUserMenu.value = false
  auth.logout()
}

// 点击菜单外部关闭菜单
const menuRef = ref<HTMLElement | null>(null)
const onClickOutside = (e: MouseEvent) => {
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) {
    showUserMenu.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', onClickOutside)
})
onUnmounted(() => {
  document.removeEventListener('click', onClickOutside)
})

onMounted(async () => {
  await auth.fetchUser()
  if (auth.isLoggedIn.value) {
    await loadConversations()
    const userId = auth.user.value?.id
    if (userId) {
      try {
        const projects = await listProjects(userId)
        if (projects?.length > 0) currentProjectId.value = projects[0].id
      } catch { /* */ }
    }
  }
})
</script>

<style scoped>
.dropdown-enter-active { transition: all 0.15s ease-out; }
.dropdown-leave-active { transition: all 0.1s ease-in; }
.dropdown-enter-from, .dropdown-leave-to { opacity: 0; transform: translateY(-4px); }
</style>