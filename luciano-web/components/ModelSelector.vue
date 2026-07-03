<!-- 模型选择器 -->
<template>
  <div class="relative">
    <button
      @click="open = !open"
      class="px-2.5 py-1 text-[11px] rounded-lg border transition-all flex items-center gap-1 select-none"
      :class="open ? 'bg-apple-blue/15 text-apple-blue border-apple-blue/30' : 'bg-transparent text-luciano-muted border-luciano-border/40 hover:border-luciano-border'"
    >
      <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2Zm0 14a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm1-4h-1V7"/></svg>
      {{ currentLabel }}
      <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>
    </button>

    <!-- 下拉 -->
    <Transition name="dropdown">
      <div v-if="open" class="absolute bottom-full mb-1 left-0 w-32 bg-luciano-card border border-luciano-border/50 rounded-xl shadow-xl overflow-hidden z-10">
        <button
          v-for="m in models"
          :key="m.key"
          @click="select(m.key)"
          class="w-full text-left px-3 py-2 text-xs hover:bg-luciano-border/20 transition-colors"
          :class="modelValue === m.key ? 'text-apple-blue font-medium' : 'text-luciano-muted'"
        >
          {{ m.label }}
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const open = ref(false)

const models = [
  { key: 'xyq', label: '小云雀' },
  { key: 'gemini', label: 'Gemini' },
  { key: 'gpt', label: 'GPT-4o' },
]

const currentLabel = computed(() => models.find(m => m.key === props.modelValue)?.label || '选择模型')

const select = (key: string) => {
  emit('update:modelValue', key)
  open.value = false
}

// 点击外部关闭
const closeOnOutside = (e: MouseEvent) => {
  if (open.value) open.value = false
}
const selectorRef = ref<HTMLElement>()

onMounted(() => {
  document.addEventListener('click', closeOnOutside, true)
})
onUnmounted(() => {
  document.removeEventListener('click', closeOnOutside, true)
})
</script>

<style scoped>
.dropdown-enter-active { transition: all 0.15s ease-out; }
.dropdown-leave-active { transition: all 0.1s ease-in; }
.dropdown-enter-from { opacity: 0; transform: translateY(4px); }
.dropdown-leave-to { opacity: 0; transform: translateY(4px); }
</style>