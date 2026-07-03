<!-- 聊天输入框 — 可上传、可配选项 -->
<template>
  <div class="bg-luciano-card rounded-2xl border border-luciano-border/40 p-3 transition-all hover:border-luciano-border/60 focus-within:border-apple-blue/30">
    <!-- 上传预览 -->
    <div v-if="uploadedFile" class="mb-2 flex items-center gap-2 px-1">
      <div class="flex items-center gap-1.5 text-xs bg-luciano-bg px-2 py-1 rounded-lg">
        <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" x2="12" y1="3" y2="15"/></svg>
        {{ uploadedFile.name }}
        <button @click="clearUpload" class="text-luciano-muted hover:text-luciano-text">✕</button>
      </div>
    </div>

    <!-- 文本区域 -->
    <textarea
      :value="modelValue"
      @input="onInput"
      @keydown.enter.exact.prevent="$emit('send')"
      :placeholder="placeholder"
      :rows="compact ? 1 : 3"
      class="w-full bg-transparent text-sm resize-none focus:outline-none placeholder:text-luciano-muted/50"
    />

    <!-- 底部操作栏 -->
    <div class="flex items-center justify-between mt-1">
      <div class="flex items-center gap-1">
        <!-- 上传按钮 -->
        <button
          @click="triggerUpload"
          class="w-7 h-7 rounded-lg hover:bg-luciano-border/20 flex items-center justify-center text-luciano-muted hover:text-luciano-text transition-colors"
          title="上传文件"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" x2="12" y1="3" y2="15"/></svg>
        </button>
        <input ref="fileInput" type="file" class="hidden" accept="image/*,video/*" @change="onFileSelected" />

        <!-- 插槽：比例、模型选择等 -->
        <slot name="options" />
      </div>

      <!-- 发送 -->
      <button
        @click="$emit('send')"
        :disabled="!modelValue.trim() || sending"
        class="w-8 h-8 rounded-full bg-apple-blue text-white flex items-center justify-center disabled:opacity-20 disabled:cursor-not-allowed hover:bg-apple-blue/90 active:scale-90 transition-all"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M3.478 2.404a.75.75 0 00-.926.941l2.432 7.905H13.5a.75.75 0 010 1.5H5.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.404z"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string
  sending?: boolean
  placeholder?: string
  compact?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  upload: [file: File]
}>()

const fileInput = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)

const triggerUpload = () => fileInput.value?.click()

const onFileSelected = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (input.files?.length) {
    uploadedFile.value = input.files[0]
  }
}

const clearUpload = () => {
  uploadedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

const onInput = (e: Event) => {
  const el = e.target as HTMLTextAreaElement
  emit('update:modelValue', el.value)
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}
</script>