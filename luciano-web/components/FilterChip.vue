<!-- 筛选标签 -->
<template>
  <button
    class="px-3 py-1 text-xs rounded-full border transition-all"
    :class="active
      ? 'bg-apple-blue text-white border-apple-blue'
      : 'bg-luciano-card text-luciano-muted border-luciano-border/50 hover:border-luciano-border'"
  >
    {{ label }}
  </button>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  active: boolean
}>()
</script>