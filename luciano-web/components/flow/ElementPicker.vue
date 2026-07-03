<!-- Kling 主体选择器 — Schema 驱动的 ELEMENT_LIST 参数渲染 -->
<template>
  <div class="element-picker">
    <!-- 加载中 -->
    <div v-if="loading" class="element-loading">
      <span class="spinner"></span> 加载主体列表...
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="element-error">
      <span>⚠️ {{ loadError }}</span>
      <button class="retry-btn" @click="loadElements">重试</button>
    </div>

    <!-- 主体列表 -->
    <div v-else class="element-list">
      <!-- 自定义主体 -->
      <div v-if="customElements.length > 0" class="element-group">
        <div class="group-label">自定义主体</div>
        <div
          v-for="el in customElements"
          :key="el.element_id"
          class="element-item"
          :class="{ selected: isSelected(el) }"
          @click="toggleSelect(el)"
        >
          <img v-if="el.frontal_image_url" :src="el.frontal_image_url" class="element-thumb" alt="" />
          <div v-else class="element-thumb-placeholder">🧑</div>
          <div class="element-info">
            <div class="element-name">{{ el.element_name || el.name || '未命名' }}</div>
            <div class="element-desc">{{ el.element_description || el.description || '' }}</div>
          </div>
          <span class="check-icon" :class="{ checked: isSelected(el) }">{{ isSelected(el) ? '☑' : '☐' }}</span>
        </div>
      </div>

      <!-- 官方预设主体 -->
      <div v-if="presetElements.length > 0" class="element-group">
        <div class="group-label">官方主体</div>
        <div
          v-for="el in presetElements"
          :key="el.element_id"
          class="element-item"
          :class="{ selected: isSelected(el) }"
          @click="toggleSelect(el)"
        >
          <img v-if="el.frontal_image_url" :src="el.frontal_image_url" class="element-thumb" alt="" />
          <div v-else class="element-thumb-placeholder">🧑</div>
          <div class="element-info">
            <div class="element-name">{{ el.element_name || el.name || '未命名' }}</div>
            <div class="element-desc">{{ el.element_description || el.description || '' }}</div>
          </div>
          <span class="check-icon" :class="{ checked: isSelected(el) }">{{ isSelected(el) ? '☑' : '☐' }}</span>
        </div>
      </div>

      <!-- 空列表 -->
      <div v-if="customElements.length === 0 && presetElements.length === 0" class="element-empty">
        <span>暂无主体，请先创建</span>
      </div>

      <!-- 创建按钮 -->
      <button class="create-btn" @click="showCreateDialog = true">
        + 创建新主体
      </button>

      <!-- 已选统计 -->
      <div v-if="selectedIds.length > 0" class="selected-count">
        已选 {{ selectedIds.length }} 个主体
      </div>
    </div>

    <!-- 创建主体弹窗 -->
    <teleport to="body">
      <div v-if="showCreateDialog" class="create-overlay" @click.self="showCreateDialog = false">
        <div class="create-dialog">
          <h3 class="dialog-title">创建新主体</h3>

          <div class="dialog-body">
            <div class="dialog-field">
              <label>主体名称 <span class="req">*</span></label>
              <input v-model="createForm.element_name" type="text" maxlength="20" class="dialog-input" placeholder="如：小红" />
            </div>

            <div class="dialog-field">
              <label>主体描述 <span class="req">*</span></label>
              <textarea v-model="createForm.element_description" maxlength="100" class="dialog-input" rows="2" placeholder="如：身穿白色连衣裙的年轻女性" />
            </div>

            <div class="dialog-field">
              <label>正面参考图 <span class="req">*</span></label>
              <div class="upload-area">
                <input type="file" accept="image/*" @change="handleFrontalUpload" class="file-input" id="frontal-upload" />
                <label for="frontal-upload" class="upload-label">
                  <span v-if="!createForm.frontal_image">点击上传正面图</span>
                  <img v-else :src="createForm.frontal_image" class="upload-preview" alt="" />
                </label>
              </div>
            </div>

            <div class="dialog-field">
              <label>其他角度参考图（可选，最多3张）</label>
              <div class="upload-area multi">
                <div v-for="(_, i) in 3" :key="i" class="upload-slot">
                  <input type="file" accept="image/*" @change="handleReferUpload($event, i)" class="file-input" :id="`refer-upload-${i}`" />
                  <label :for="`refer-upload-${i}`" class="upload-label">
                    <span v-if="!createForm.refer_images?.[i]">+</span>
                    <img v-else :src="createForm.refer_images[i]" class="upload-preview" alt="" />
                  </label>
                </div>
              </div>
            </div>
          </div>

          <div class="dialog-footer">
            <button class="btn-cancel" @click="showCreateDialog = false">取消</button>
            <button class="btn-confirm" :disabled="creating" @click="handleCreate">
              {{ creating ? '创建中...' : '创建' }}
            </button>
          </div>

          <!-- 创建状态 -->
          <div v-if="createStatus" class="create-status" :class="createStatusType">
            {{ createStatus }}
          </div>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useApi } from '~/composables/useApi'

const props = defineProps<{
  modelValue: any[]  // [{element_id: 123}, ...] 或 null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: any[]]
}>()

const api = useApi()

// ========== 数据 ==========
const loading = ref(true)
const loadError = ref('')
const customElements = ref<any[]>([])
const presetElements = ref<any[]>([])

// ========== 选中状态 ==========
const selectedIds = computed(() => {
  if (!props.modelValue || !Array.isArray(props.modelValue)) return []
  return props.modelValue.map((item: any) => {
    if (typeof item === 'number') return item
    if (typeof item === 'object' && item.element_id) return item.element_id
    return item
  })
})

function isSelected(el: any): boolean {
  return selectedIds.value.includes(el.element_id)
}

function toggleSelect(el: any) {
  const current = [...selectedIds.value]
  const idx = current.indexOf(el.element_id)
  if (idx >= 0) {
    current.splice(idx, 1)
  } else {
    current.push(el.element_id)
  }
  // 转换为 [{element_id: xxx}] 格式
  emit('update:modelValue', current.map(id => ({ element_id: id })))
}

// ========== 加载主体列表 ==========
async function loadElements() {
  loading.value = true
  loadError.value = ''
  try {
    const [customRes, presetRes] = await Promise.all([
      api.listKlingElements(1, 30),
      api.listKlingPresets(1, 30)
    ])
    // 解析 Kling API 响应结构
    customElements.value = extractElements(customRes)
    presetElements.value = extractElements(presetRes)
  } catch (e: any) {
    loadError.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function extractElements(res: any): any[] {
  if (!res) return []
  // Kling 响应结构：{ code, data: { elements: [...] } } 或直接数组
  if (res.data?.elements) return res.data.elements
  if (res.elements) return res.elements
  if (Array.isArray(res.data)) return res.data
  if (Array.isArray(res)) return res
  return []
}

onMounted(loadElements)

// ========== 创建主体弹窗 ==========
const showCreateDialog = ref(false)
const creating = ref(false)
const createStatus = ref('')
const createStatusType = ref('')

const createForm = ref({
  element_name: '',
  element_description: '',
  frontal_image: '',
  refer_images: [] as string[]
})

function resetCreateForm() {
  createForm.value = {
    element_name: '',
    element_description: '',
    frontal_image: '',
    refer_images: []
  }
  createStatus.value = ''
  createStatusType.value = ''
}

async function handleFrontalUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const url = await uploadImage(file)
  if (url) createForm.value.frontal_image = url
}

async function handleReferUpload(e: Event, index: number) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const url = await uploadImage(file)
  if (url) {
    if (!createForm.value.refer_images) createForm.value.refer_images = []
    createForm.value.refer_images[index] = url
  }
}

async function uploadImage(file: File): Promise<string> {
  try {
    const formData = new FormData()
    formData.append('file', file)
    // 复用现有上传接口
    const res = await api.rawRequest('/upload', { method: 'POST', body: formData })
    return (res as any)?.url || ''
  } catch (e) {
    console.error('[ElementPicker] upload failed:', e)
    return ''
  }
}

async function handleCreate() {
  if (!createForm.value.element_name || !createForm.value.element_description || !createForm.value.frontal_image) {
    createStatus.value = '请填写名称、描述并上传正面图'
    createStatusType.value = 'error'
    return
  }

  creating.value = true
  createStatus.value = '正在创建...'
  createStatusType.value = 'info'

  try {
    const body: { [key: string]: any } = {
      element_name: createForm.value.element_name,
      element_description: createForm.value.element_description,
      reference_type: 'image_refer',
      frontal_image: createForm.value.frontal_image,
    }
    if (createForm.value.refer_images.length > 0) {
      body.refer_images = createForm.value.refer_images.filter(Boolean)
    }

    const res = await api.createKlingElement(body)
    const taskId = (res as any)?.data?.task_id || (res as any)?.task_id
    if (!taskId) {
      createStatus.value = '创建失败：未返回 task_id'
      createStatusType.value = 'error'
      return
    }

    // 轮询创建状态
    createStatus.value = '等待主体创建完成...'
    let pollCount = 0
    const pollInterval = setInterval(async () => {
      pollCount++
      try {
        const pollRes = await api.pollKlingElementTask(taskId)
        const status = (pollRes as any)?.data?.status || (pollRes as any)?.status
        if (status === 'succeed') {
          clearInterval(pollInterval)
          createStatus.value = '创建成功！'
          createStatusType.value = 'success'
          // 刷新列表
          await loadElements()
          setTimeout(() => {
            showCreateDialog.value = false
            resetCreateForm()
          }, 1500)
        } else if (status === 'failed') {
          clearInterval(pollInterval)
          createStatus.value = '创建失败：' + ((pollRes as any)?.data?.task_info || '未知原因')
          createStatusType.value = 'error'
        } else if (pollCount > 60) {
          // 3分钟超时
          clearInterval(pollInterval)
          createStatus.value = '创建超时，请稍后在列表中查看'
          createStatusType.value = 'error'
        }
      } catch (e) {
        // 轮询失败继续
      }
    }, 3000)
  } catch (e: any) {
    createStatus.value = '创建失败：' + (e?.message || '未知错误')
    createStatusType.value = 'error'
  } finally {
    creating.value = false
  }
}

// 弹窗关闭时重置
watch(showCreateDialog, (val) => {
  if (!val) resetCreateForm()
})
</script>

<style scoped>
.element-picker {
  width: 100%;
}

.element-loading {
  padding: 12px;
  text-align: center;
  color: var(--flow-text-dim);
  font-size: 12px;
}

.spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid var(--flow-border-hover);
  border-top-color: var(--flow-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-right: 6px;
  vertical-align: middle;
}

@keyframes spin { to { transform: rotate(360deg); } }

.element-error {
  padding: 10px;
  text-align: center;
  color: #f87171;
  font-size: 12px;
}

.retry-btn {
  margin-left: 8px;
  padding: 2px 8px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 4px;
  color: var(--flow-text);
  cursor: pointer;
  font-size: 11px;
}

.element-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.element-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.group-label {
  font-size: 10px;
  color: var(--flow-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 2px 0;
}

.element-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.element-item:hover {
  border-color: var(--flow-border-hover);
}

.element-item.selected {
  border-color: var(--flow-accent);
  background: rgba(59, 130, 246, 0.08);
}

.element-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.element-thumb-placeholder {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  background: var(--flow-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.element-info {
  flex: 1;
  min-width: 0;
}

.element-name {
  font-size: 12px;
  color: var(--flow-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.element-desc {
  font-size: 10px;
  color: var(--flow-text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.check-icon {
  font-size: 16px;
  color: var(--flow-text-dim);
  flex-shrink: 0;
}

.check-icon.checked {
  color: var(--flow-accent);
}

.element-empty {
  padding: 12px;
  text-align: center;
  color: var(--flow-text-dim);
  font-size: 12px;
}

.create-btn {
  width: 100%;
  padding: 8px;
  background: transparent;
  border: 1px dashed var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text-dim);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s;
}

.create-btn:hover {
  border-color: var(--flow-accent);
  color: var(--flow-accent);
}

.selected-count {
  font-size: 10px;
  color: var(--flow-text-dim);
  text-align: right;
  padding-top: 2px;
}

/* ========== 创建弹窗 ========== */
.create-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.create-dialog {
  background: #1a1a2e;
  border: 1px solid var(--flow-border-hover);
  border-radius: 12px;
  padding: 20px;
  width: 90%;
  max-width: 440px;
  max-height: 90vh;
  overflow-y: auto;
}

.dialog-title {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--flow-text);
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dialog-field label {
  font-size: 12px;
  color: var(--flow-text-dim);
}

.req {
  color: #f87171;
}

.dialog-input {
  padding: 8px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text);
  font-size: 13px;
  outline: none;
}

.dialog-input:focus {
  border-color: var(--flow-accent);
}

.upload-area {
  width: 100%;
}

.upload-area.multi {
  display: flex;
  gap: 8px;
}

.upload-slot {
  flex: 1;
}

.file-input {
  display: none;
}

.upload-label {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 80px;
  background: var(--flow-bg);
  border: 1px dashed var(--flow-border-hover);
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: var(--flow-text-dim);
  overflow: hidden;
}

.upload-label:hover {
  border-color: var(--flow-accent);
}

.upload-preview {
  width: 100%;
  height: 80px;
  object-fit: cover;
  border-radius: 5px;
}

.upload-area.multi .upload-label {
  min-height: 60px;
}

.upload-area.multi .upload-preview {
  height: 60px;
}

.dialog-footer {
  display: flex;
  gap: 8px;
  margin-top: 20px;
  justify-content: flex-end;
}

.btn-cancel, .btn-confirm {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid var(--flow-border-hover);
  transition: all 0.15s;
}

.btn-cancel {
  background: transparent;
  color: var(--flow-text-dim);
}

.btn-confirm {
  background: var(--flow-accent);
  color: white;
  border-color: var(--flow-accent);
}

.btn-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.create-status {
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  text-align: center;
}

.create-status.info {
  background: rgba(59, 130, 246, 0.1);
  color: #60a5fa;
}

.create-status.success {
  background: rgba(34, 197, 94, 0.1);
  color: #4ade80;
}

.create-status.error {
  background: rgba(248, 113, 113, 0.1);
  color: #f87171;
}
</style>