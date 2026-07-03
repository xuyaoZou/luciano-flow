<!-- 项目详情布局：三标签切换 -->
<template>
  <div class="min-h-screen flex flex-col">
    <!-- 顶栏 -->
    <header class="flex items-center justify-between px-6 py-4 border-b border-luciano-border/50">
      <div class="flex items-center gap-3">
        <NuxtLink to="/" class="text-luciano-muted hover:text-luciano-text transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
        </NuxtLink>
        <h1 class="text-lg font-semibold tracking-tight">{{ project?.title || '...' }}</h1>
      </div>
      <!-- 模型标签 -->
      <div v-if="project?.modelProvider" class="text-xs text-luciano-muted bg-luciano-card px-2.5 py-1 rounded-full">
        {{ project.modelProvider.toUpperCase() }}
      </div>
    </header>

    <!-- 标签栏 — 丝滑切换 -->
    <nav class="flex px-6 pt-2 gap-1 border-b border-luciano-border/30">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        @click="switchTab(tab.key)"
        class="relative px-4 py-2.5 text-sm font-medium transition-colors rounded-t-lg"
        :class="activeTab === tab.key
          ? 'text-luciano-text'
          : 'text-luciano-muted hover:text-luciano-text'"
      >
        <span class="flex items-center gap-1.5">
          {{ tab.icon }} {{ tab.label }}
        </span>
        <!-- 底部指示条 -->
        <div
          v-if="activeTab === tab.key"
          class="absolute bottom-0 left-2 right-2 h-[2px] bg-apple-blue rounded-full transition-all duration-300"
        />
      </button>
    </nav>

    <!-- 内容区 -->
    <main class="flex-1 overflow-hidden">
      <Transition name="slide-fade" mode="out-in">
        <AgentChat v-if="activeTab === 'agent'" :project-id="projectId" :project="project" />
        <MediaLibrary v-else-if="activeTab === 'library'" :project-id="projectId" />
        <ProfessionalMode v-else-if="activeTab === 'pro'" :project-id="projectId" />
      </Transition>
    </main>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  projectId: number
}>()

const { fetchProject } = useApi()
const { data: project } = await useAsyncData(() => fetchProject(props.projectId))

const tabs = [
  { key: 'agent', icon: '🤖', label: 'Agent' },
  { key: 'library', icon: '📁', label: '资源库' },
  { key: 'pro', icon: '✏️', label: '专业模式' },
]

const activeTab = ref('agent')

const switchTab = (key: string) => {
  activeTab.value = key
}
</script>

<style scoped>
.slide-fade-enter-active {
  transition: all 0.25s ease-out;
}
.slide-fade-leave-active {
  transition: all 0.15s ease-in;
}
.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(12px);
}
.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}
</style>