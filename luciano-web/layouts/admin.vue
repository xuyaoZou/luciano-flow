<!-- Admin 布局 — 顶部导航 + 主内容 -->
<template>
  <div class="min-h-screen bg-luciano-bg text-luciano-text">
    <!-- 顶部导航 -->
    <header class="sticky top-0 z-40 bg-luciano-bg/95 backdrop-blur border-b border-luciano-border/30">
      <div class="max-w-7xl mx-auto px-4 h-12 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <NuxtLink to="/" class="text-sm text-luciano-muted hover:text-luciano-text transition-colors">
            ← 返回主页
          </NuxtLink>
          <span class="text-base font-semibold">管理后台</span>
        </div>
        <div class="flex items-center gap-3">
          <span v-if="auth.isLoggedIn.value" class="text-xs text-luciano-muted">{{ auth.user.value?.username }}</span>
        </div>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="max-w-7xl mx-auto">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
const auth = useAuth()
</script>