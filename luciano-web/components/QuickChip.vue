<!-- 快捷选择标签 -->
<template>
  <button
    class="px-2.5 py-1 text-[11px] rounded-lg border transition-all select-none"
    :class="active
      ? 'bg-apple-blue/15 text-apple-blue border-apple-blue/30'
      : 'bg-transparent text-luciano-muted border-luciano-border/40 hover:border-luciano-border'"
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