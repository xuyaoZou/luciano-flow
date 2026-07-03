<!-- 左边栏 — 可折叠，小云雀风格 -->
<template>
  <aside
    class="h-full bg-luciano-card/50 border-r border-luciano-border/30 flex flex-col shrink-0 transition-all duration-300 ease-in-out overflow-hidden"
    :class="collapsed ? 'w-0 border-r-0' : 'w-56'"
  >
    <div v-show="!collapsed" class="flex flex-col h-full">
      <!-- 顶部：Logo + 折叠按钮 -->
      <div class="flex items-center justify-between px-4 py-4">
        <div class="flex items-center gap-2">
          <div class="text-xl">⚡</div>
          <span class="text-sm font-semibold tracking-tight">Luciano</span>
        </div>
        <button
          @click="$emit('toggle')"
          class="w-6 h-6 rounded-md hover:bg-luciano-border/30 flex items-center justify-center text-luciano-muted transition-colors"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
        </button>
      </div>

      <!-- 导航按钮 -->
      <nav class="px-3 space-y-1">
        <SidebarButton
          icon="creative"
          label="创作"
          :active="activeView === 'chat'"
          @click="$emit('navigate', 'chat')"
        />
        <SidebarButton
          icon="professional"
          label="专业模式"
          :active="activeView === 'professional'"
          @click="$emit('navigate', 'professional')"
        />
        <SidebarButton
          icon="create"
          label="工作台"
          :active="activeView === 'create'"
          @click="$emit('navigate', 'create')"
        />
        <SidebarButton
          icon="project"
          label="项目"
          :active="activeView === 'project'"
          @click="$emit('navigate', 'project')"
        />
        <SidebarButton
          icon="assets"
          label="资产"
          :active="activeView === 'assets'"
          @click="$emit('navigate', 'assets')"
        />
        <SidebarButton
          icon="settings"
          label="存储配置"
          :active="activeView === 'storage'"
          @click="$emit('navigate', 'storage')"
        />
        <SidebarButton
          icon="admin"
          label="管理后台"
          :active="false"
          @click="goAdmin"
        />
      </nav>

      <!-- 分隔线 -->
      <div class="mx-4 my-3 h-px bg-luciano-border/30" />

      <!-- 会话历史 -->
      <div class="flex-1 overflow-y-auto px-3">
        <p class="text-[10px] text-luciano-muted/60 uppercase tracking-wider font-semibold px-2 mb-2">会话历史</p>
        <div class="space-y-0.5">
          <button
            v-for="conv in conversations"
            :key="conv.id"
            @click="$emit('openConversation', conv)"
            class="w-full text-left px-2 py-2 rounded-lg hover:bg-luciano-border/20 transition-all group"
            :class="activeConversationId === conv.id ? 'bg-luciano-border/20' : ''"
          >
            <div class="flex items-center justify-between">
              <span class="text-xs text-luciano-text group-hover:text-apple-blue truncate flex-1">
                {{ conv.title || (conv.provider === 'xyq' ? '小云雀' : conv.provider) }}
              </span>
              <span
                class="text-[9px] px-1.5 py-0.5 rounded-full shrink-0 ml-1"
                :class="conv.status === 'active' ? 'bg-green-500/10 text-green-500' : 'bg-luciano-muted/10 text-luciano-muted'"
              >
                {{ conv.status === 'active' ? '活跃' : '已关闭' }}
              </span>
            </div>
            <div class="text-[10px] text-luciano-muted/60 mt-0.5">
              {{ formatConvDate(conv.updatedAt || conv.createdAt) }}
            </div>
          </button>
          <p v-if="conversations.length === 0" class="px-2 text-[10px] text-luciano-muted/40">暂无会话</p>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
defineProps<{
  collapsed: boolean
  activeView: string
  activeConversationId?: number | null
  conversations: {
    id: number
    title?: string
    provider: string
    status: string
    projectId: number
    createdAt: string
    updatedAt?: string
  }[]
}>()

defineEmits<{
  toggle: []
  navigate: [view: string]
  openConversation: [conv: any]
}>()

const goAdmin = () => {
  navigateTo('/admin')
}

const formatConvDate = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>