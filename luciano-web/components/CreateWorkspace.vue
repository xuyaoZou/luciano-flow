<!-- 创作工作台 — 动态表单 + 生成 + 结果预览 -->
<template>
  <div class="flex flex-col h-full">
    <!-- Header -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-luciano-border/30">
      <div>
        <h2 class="text-lg font-semibold">创作工作台</h2>
        <p class="text-xs text-luciano-muted mt-0.5">选择模型能力，配置参数，一键生成</p>
      </div>
      <button @click="reset" class="text-xs text-luciano-muted hover:text-luciano-text transition-colors px-3 py-1.5 rounded-lg hover:bg-luciano-card">
        重置
      </button>
    </div>

    <!-- 主体：左右分栏 -->
    <div class="flex-1 flex overflow-hidden">
      <!-- 左：配置面板 -->
      <div class="w-[420px] shrink-0 border-r border-luciano-border/30 overflow-y-auto">
        <div class="p-5 space-y-5">
          <!-- Step 1: 选择能力 -->
          <div>
            <label class="text-xs font-medium text-luciano-muted uppercase tracking-wider mb-2 block">1. 选择能力</label>
            <div class="space-y-2">
              <select
                v-model="selectedAdapter"
                @change="selectAdapter(selectedAdapter)"
                class="w-full px-3 py-2.5 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
              >
                <option value="" disabled>选择模型</option>
                <option v-for="a in adapters" :key="a.id" :value="a.id">{{ a.displayName }}</option>
              </select>

              <select
                v-model="selectedCapability"
                @change="selectCapability(selectedCapability)"
                :disabled="!selectedAdapter"
                class="w-full px-3 py-2.5 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors disabled:opacity-40"
              >
                <option value="" disabled>选择能力</option>
                <option v-for="c in adapterCapabilities" :key="c.code" :value="c.code">
                  {{ c.displayName }}
                </option>
              </select>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="schemaLoading" class="flex items-center justify-center py-8">
            <div class="w-5 h-5 border-2 border-apple-blue/30 border-t-apple-blue rounded-full animate-spin" />
          </div>

          <!-- Step 2: 配置参数 -->
          <div v-if="schema && !schemaLoading">
            <label class="text-xs font-medium text-luciano-muted uppercase tracking-wider mb-3 block">2. 配置参数</label>

            <!-- 按组渲染 -->
            <div v-for="(params, group) in groupedParams" :key="group" class="mb-4">
              <div class="text-[11px] text-luciano-muted/60 uppercase tracking-widest mb-1.5">{{ group }}</div>
              <div class="space-y-2.5">
                <template v-for="p in params" :key="p.name">
                  <!-- 条件不满足时隐藏 -->
                  <div v-if="!shouldShow(p) || (p.advanced && !showAdvanced)" class="hidden" />
                  <div v-else class="space-y-1">
                    <div class="flex items-center justify-between">
                      <label class="text-xs font-medium text-luciano-text/80">
                        {{ p.displayName }}
                        <span v-if="p.required" class="text-red-400">*</span>
                      </label>
                      <span v-if="p.description" class="text-[10px] text-luciano-muted/50 max-w-[180px] truncate" :title="p.description">{{ p.description }}</span>
                    </div>

                    <!-- STRING 类型 — 多语言用 textarea -->
                    <textarea
                      v-if="p.type === 'STRING' && p.multilingual"
                      v-model="formParams[p.name]"
                      :placeholder="p.displayName"
                      rows="3"
                      class="w-full px-3 py-2 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors resize-none"
                    />

                    <!-- STRING 类型 — 普通 input -->
                    <input
                      v-else-if="p.type === 'STRING'"
                      v-model="formParams[p.name]"
                      :placeholder="p.displayName"
                      class="w-full px-3 py-2 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
                    />

                    <!-- ENUM 类型 — 按钮组 -->
                    <div v-else-if="p.type === 'ENUM'" class="flex flex-wrap gap-1.5">
                      <button
                        v-for="opt in p.options"
                        :key="opt"
                        @click="formParams[p.name] = opt"
                        class="px-2.5 py-1 text-[11px] rounded-lg border transition-all"
                        :class="formParams[p.name] === opt
                          ? 'bg-apple-blue/15 text-apple-blue border-apple-blue/30'
                          : 'bg-transparent text-luciano-muted border-luciano-border/40 hover:border-luciano-border'"
                      >
                        {{ optionLabels[opt] || opt }}
                      </button>
                    </div>

                    <!-- INTEGER 类型 -->
                    <input
                      v-else-if="p.type === 'INTEGER'"
                      v-model.number="formParams[p.name]"
                      type="number"
                      :min="p.min"
                      :max="p.max"
                      class="w-full px-3 py-2 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
                    />

                    <!-- FLOAT 类型 — 滑块 -->
                    <div v-else-if="p.type === 'FLOAT'" class="flex items-center gap-3">
                      <input
                        v-model.number="formParams[p.name]"
                        type="range"
                        :min="p.min ?? 0"
                        :max="p.max ?? 1"
                        :step="p.step ?? 0.1"
                        class="flex-1 h-1.5 bg-luciano-border/50 rounded-full appearance-none cursor-pointer accent-apple-blue"
                      />
                      <span class="text-xs text-luciano-muted w-10 text-right">{{ formParams[p.name] ?? p.defaultValue }}</span>
                    </div>

                    <!-- IMAGE / IMAGE_URL 类型 — 上传 + 预览 -->
                    <div v-else-if="p.type === 'IMAGE_URL' || p.type === 'IMAGE'" class="space-y-2">
                      <!-- 已上传/已填 URL 时预览 -->
                      <div v-if="formParams[p.name]" class="relative group">
                        <img
                          :src="imagePreviewUrls[p.name] || formParams[p.name]"
                          alt="预览"
                          class="w-full max-h-40 object-contain rounded-xl border border-luciano-border/30"
                        />
                        <button
                          @click="clearImageParam(p.name)"
                          class="absolute top-1 right-1 w-6 h-6 flex items-center justify-center bg-black/60 rounded-full text-white text-xs opacity-0 group-hover:opacity-100 transition-opacity"
>
                          ✕
                        </button>
                      </div>
                      <div v-else class="flex gap-2">
                        <label
                          class="flex-1 flex items-center justify-center px-3 py-2 bg-luciano-card border border-dashed border-luciano-border/50 rounded-xl text-sm text-luciano-muted hover:border-apple-blue/50 hover:text-apple-blue cursor-pointer transition-colors"
                          :class="{ 'opacity-50 pointer-events-none': uploadingField === p.name }"
                        >
                          {{ uploadingField === p.name ? '上传中...' : '📤 上传图片' }}
                          <input
                            type="file"
                            accept="image/*"
                            class="hidden"
                            @change="(e: any) => handleImageUpload(e, p.name)"
                          />
                        </label>
                      </div>
                      <input
                        v-model="formParams[p.name]"
                        type="url"
                        placeholder="或直接输入图片 URL"
                        class="w-full px-3 py-2 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
                      />
                    </div>

                    <!-- VIDEO_URL 类型 -->
                    <input
                      v-else-if="p.type === 'VIDEO_URL'"
                      v-model="formParams[p.name]"
                      type="url"
                      placeholder="输入视频 URL"
                      class="w-full px-3 py-2 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
                    />

                    <!-- BOOLEAN 类型 -->
                    <div v-else-if="p.type === 'BOOLEAN'" class="flex items-center gap-2">
                      <button
                        @click="formParams[p.name] = !formParams[p.name]"
                        class="relative w-10 h-5 rounded-full transition-colors"
                        :class="formParams[p.name] ? 'bg-apple-blue' : 'bg-luciano-border/50'"
                      >
                        <div
                          class="absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform"
                          :class="formParams[p.name] ? 'translate-x-5' : 'translate-x-0.5'"
                        />
                      </button>
                      <span class="text-xs text-luciano-muted">{{ formParams[p.name] ? '是' : '否' }}</span>
                    </div>

                    <!-- 默认 -->
                    <input
                      v-else
                      v-model="formParams[p.name]"
                      :placeholder="p.displayName"
                      class="w-full px-3 py-2 bg-luciano-card border border-luciano-border/50 rounded-xl text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
                    />
                  </div>
                </template>
              </div>
            </div>

            <!-- 高级参数开关 -->
            <button
              v-if="allParams.some(p => p.advanced)"
              @click="showAdvanced = !showAdvanced"
              class="text-[11px] text-apple-blue hover:text-apple-blue/80 transition-colors mt-2"
            >
              {{ showAdvanced ? '隐藏高级参数 ▲' : '显示高级参数 ▼' }}
            </button>

            <!-- 约束提示 -->
            <div v-if="schema.constraints?.length" class="mt-3 space-y-1">
              <div v-for="(c, i) in schema.constraints" :key="i" class="text-[11px] text-amber-400/70">
                ⚠️ {{ c.message }}
              </div>
            </div>

            <!-- 费用预估 -->
            <div v-if="schema.costHint" class="mt-3 text-[11px] text-luciano-muted/60">
              💰 {{ schema.costHint }}
            </div>
          </div>

          <!-- Step 3: 提交 -->
          <div v-if="schema" class="pt-2">
            <label class="text-xs font-medium text-luciano-muted uppercase tracking-wider mb-2 block">3. 生成</label>
            <button
              @click="submitGenerate()"
              :disabled="!canSubmit || taskPolling"
              class="w-full py-3 rounded-xl text-sm font-medium transition-all"
              :class="canSubmit && !taskPolling
                ? 'bg-apple-blue text-white hover:bg-apple-blue/90 shadow-lg shadow-apple-blue/20'
                : 'bg-luciano-border/30 text-luciano-muted cursor-not-allowed'"
            >
              <span v-if="taskPolling">生成中...</span>
              <span v-else-if="taskHandle?.status === 'COMPLETED'">重新生成</span>
              <span v-else>开始生成</span>
            </button>

            <!-- 错误提示 -->
            <div v-if="error" class="mt-2 px-3 py-2 bg-red-500/10 border border-red-500/20 rounded-xl text-xs text-red-400">
              {{ error }}
            </div>
          </div>
        </div>
      </div>

      <!-- 右：结果预览 -->
      <div class="flex-1 flex flex-col items-center justify-center p-8 overflow-y-auto bg-luciano-bg/50">
        <!-- 空状态 -->
        <div v-if="!taskHandle" class="text-center text-luciano-muted/40">
          <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" class="mx-auto mb-3"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/></svg>
          <p class="text-sm">配置参数后点击生成</p>
          <p class="text-xs mt-1">结果将在这里展示</p>
        </div>

        <!-- 状态信息 -->
        <div v-if="taskHandle" class="mb-4 px-4 py-2 bg-luciano-card/50 border border-luciano-border/30 rounded-xl text-xs text-luciano-muted flex items-center gap-3 flex-wrap">
          <span class="font-mono" v-if="taskHandle.dbTaskId">#{{ taskHandle.dbTaskId }}</span>
          <span class="font-mono" v-else>ID: {{ taskHandle.taskId?.substring(0, 12) }}...</span>
          <span :class="{
            'text-yellow-400': taskHandle.status === 'PENDING',
            'text-blue-400': taskHandle.status === 'PROCESSING',
            'text-green-400': taskHandle.status === 'COMPLETED',
            'text-red-400': taskHandle.status === 'FAILED'
          }">● {{ taskHandle.status }}</span>
          <span v-if="taskHandle.adapterId">{{ taskHandle.adapterId }} / {{ taskHandle.capability }}</span>
        </div>

        <!-- 生成中 -->
        <div v-else-if="taskHandle?.status === 'PENDING' || taskHandle?.status === 'PROCESSING'" class="text-center">
          <div class="w-12 h-12 border-[3px] border-apple-blue/20 border-t-apple-blue rounded-full animate-spin mx-auto mb-4" />
          <p class="text-sm text-luciano-muted">{{ taskHandle.status === 'PENDING' ? '排队中...' : '生成中...' }}</p>
          <p v-if="schema?.estimatedDurationMs" class="text-xs text-luciano-muted/50 mt-1">
            预计 {{ Math.ceil(schema.estimatedDurationMs / 1000 / 60) }} 分钟
          </p>
        </div>

        <!-- 失败 -->
        <div v-else-if="taskHandle?.status === 'FAILED'" class="text-center">
          <div class="text-4xl mb-3">❌</div>
          <p class="text-sm text-red-400">生成失败</p>
          <p v-if="taskHandle.errorMsg" class="text-xs text-luciano-muted mt-1">{{ taskHandle.errorMsg }}</p>
        </div>

        <!-- 成功 -->
        <div v-else-if="taskHandle?.status === 'COMPLETED' && (resultBlobUrl || taskHandle?.resultUrl)" class="w-full max-w-2xl">
          <!-- 视频 -->
          <video
            v-if="schema?.outputFormats?.includes('mp4')"
            :src="resultBlobUrl || taskHandle.resultUrl"
            controls
            class="w-full rounded-2xl shadow-lg"
          />
          <!-- 图片 -->
          <img
            v-else
            :src="resultBlobUrl || taskHandle.resultUrl"
            alt="生成结果"
            class="max-w-full max-h-[60vh] rounded-2xl shadow-lg mx-auto"
          />
          <div class="mt-4 flex items-center justify-center gap-3">
            <a
              v-if="!resultBlobUrl"
              :href="taskHandle.resultUrl"
              target="_blank"
              class="px-4 py-2 text-xs bg-luciano-card border border-luciano-border/50 rounded-xl hover:border-luciano-border transition-colors"
            >
              🔗 打开原图
            </a>
            <button
              v-if="taskHandle.mediaAssetId"
              @click="$router.push(`/assets?assetId=${taskHandle.mediaAssetId}`)"
              class="px-4 py-2 text-xs bg-green-600/20 border border-green-500/30 rounded-xl hover:border-green-500/50 transition-colors text-green-400"
            >
              📦 已保存到资产库 #{{ taskHandle.mediaAssetId }}
            </button>
            <button
              @click="taskHandle = null"
              class="px-4 py-2 text-xs bg-luciano-card border border-luciano-border/50 rounded-xl hover:border-luciano-border transition-colors"
            >
              ✕ 清除结果
            </button>
          </div>
        </div>

        <!-- COMPLETED 但没有 URL -->
        <div v-else-if="taskHandle?.status === 'COMPLETED' && !taskHandle?.resultUrl" class="text-center">
          <div class="text-4xl mb-3">⚠️</div>
          <p class="text-sm text-luciano-muted">生成完成，但未获取到结果</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const {
  adapters,
  selectedAdapter,
  selectedCapability,
  schema,
  schemaLoading,
  formParams,
  taskHandle,
  taskPolling,
  error,
  adapterCapabilities,
  allParams,
  groupedParams,
  canSubmit,
  loadAdapters,
  selectAdapter,
  selectCapability,
  submitGenerate,
  reset: resetState,
} = useAdapter()

const { loadMedia, getBlobUrl } = useMediaLoader()

// 生成结果的 blob URL（走本地媒体服务，不过期）
const resultBlobUrl = ref('')

// taskHandle 变化时加载 blob URL
watch(() => taskHandle.value, async (handle) => {
  if (handle?.status === 'COMPLETED' && handle.mediaAssetId) {
    resultBlobUrl.value = await loadMedia({ id: handle.mediaAssetId })
  } else {
    resultBlobUrl.value = ''
  }
})

const showAdvanced = ref(false)

/** 条件显示：参数是否应该渲染 */
const shouldShow = (p: any) => {
  if (!p.showWhenField) return true
  return formParams.value[p.showWhenField] === p.showWhenValue
}

/** ENUM 选项显示名映射（原始值 → 友好名） */
const optionLabels: Record<string, string> = {
  simple: '简单运镜',
  forward_up: '推进上移 ↗',
  down_back: '下移拉远 ↙',
  right_turn_forward: '右旋推进 ↪',
  left_turn_forward: '左旋推进 ↩',
  horizontal: '水平移动 ↔',
  vertical: '垂直移动 ↕',
  pan: '水平旋转 ⟳',
  tilt: '垂直旋转 ↕↕',
  roll: '翻滚 🔄',
  zoom: '缩放 🔍',
}

onMounted(async () => {
  await loadAdapters()
  if (adapters.value.length === 1) {
    selectAdapter(adapters.value[0].id)
  }
})

// 图片上传
const uploadingField = ref<string | null>(null)
const imagePreviewUrls = ref<Record<string, string>>({})
const { rawRequest } = useApi()

const handleImageUpload = async (event: Event, fieldName: string) => {
  const input = event.target as HTMLInputElement
  if (!input.files?.length) return

  const file = input.files[0]
  if (!file.type.startsWith('image/')) return

  uploadingField.value = fieldName
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('source', 'adapter_workspace')

    const result = await rawRequest<{id: number, url: string, localPath: string, mediaType: string}>
      ('/upload/image', { method: 'POST', body: formData })

    // 用本地媒体 URL
    formParams.value[fieldName] = result.url

    // 加载带认证的预览 URL
    const previewUrl = await loadMedia({ id: result.id })
    imagePreviewUrls.value[fieldName] = previewUrl
  } catch (e: any) {
    console.error('[Upload] Failed:', e)
    alert('上传失败: ' + (e.message || '未知错误'))
  } finally {
    uploadingField.value = null
    input.value = '' // 重置 input，允许重复选择同一文件
  }
}

const clearImageParam = (fieldName: string) => {
  formParams.value[fieldName] = ''
  imagePreviewUrls.value[fieldName] = ''
}

const reset = () => {
  resetState()
  showAdvanced.value = false
}
</script>