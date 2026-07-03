<!-- 短剧 Agent 页 — 结构化创作入口 -->
<template>
  <div class="flex flex-col items-center justify-center h-full px-6 animate-fade-in">
    <div class="text-4xl mb-4">🎬</div>
    <h2 class="text-xl font-semibold mb-2">短剧 Agent</h2>
    <p class="text-sm text-luciano-muted max-w-sm text-center mb-8">
      从故事大纲到分镜视频，AI 全流程帮你完成短剧创作
    </p>
    <div class="flex gap-3">
      <button class="px-5 py-2.5 bg-apple-blue text-white text-sm font-medium rounded-full hover:bg-apple-blue/90 active:scale-95 transition-all">
        开始创作
      </button>
      <button v-if="projectId" class="px-5 py-2.5 bg-luciano-card border border-luciano-border/50 text-sm rounded-full hover:border-luciano-border transition-colors">
        继续编辑
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ projectId?: number | null }>()
</script>