<!-- 存储配置页面 — 管理存储提供者 -->
<template>
  <div class="h-full flex flex-col overflow-hidden">
    <!-- 顶部标题栏 -->
    <div class="px-6 py-4 border-b border-luciano-border/30 shrink-0">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-lg font-semibold">存储配置</h1>
          <p class="text-xs text-luciano-muted mt-1">管理文件存储方式：本地存储、对象存储（TOS/MinIO/S3）</p>
        </div>
        <button
          @click="showAddForm = true"
          class="px-4 py-2 bg-apple-blue text-white text-sm rounded-lg hover:bg-apple-blue/90 transition-colors flex items-center gap-1.5"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="5" y2="19"/><line x1="5" x2="19" y1="12" y2="12"/></svg>
          新增配置
        </button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-sm text-luciano-muted">加载中...</div>
    </div>

    <!-- 配置列表 -->
    <div v-else class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
      <!-- 空状态 -->
      <div v-if="providers.length === 0" class="text-center py-16">
        <div class="text-4xl mb-3">📂</div>
        <p class="text-sm text-luciano-muted">暂无存储配置</p>
      </div>

      <!-- 配置卡片 -->
      <div
        v-for="provider in providers"
        :key="provider.id"
        class="rounded-xl border transition-all"
        :class="provider.isDefault
          ? 'border-apple-blue/30 bg-apple-blue/5'
          : 'border-luciano-border/30 bg-luciano-card/50'"
      >
        <!-- 卡片头部 -->
        <div class="px-5 py-4 flex items-center justify-between">
          <div class="flex items-center gap-3">
            <!-- 类型图标 -->
            <div class="w-10 h-10 rounded-lg flex items-center justify-center text-lg"
              :class="providerIconClass(provider.providerType)">
              {{ providerIcon(provider.providerType) }}
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="font-medium">{{ provider.name }}</span>
                <span v-if="provider.isDefault" class="px-1.5 py-0.5 text-[10px] bg-apple-blue/10 text-apple-blue rounded font-medium">默认</span>
                <span v-if="!provider.enabled" class="px-1.5 py-0.5 text-[10px] bg-red-500/10 text-red-400 rounded font-medium">已禁用</span>
              </div>
              <span class="text-xs text-luciano-muted">{{ providerTypeLabel(provider.providerType) }}</span>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <!-- 设为默认 -->
            <button
              v-if="!provider.isDefault && provider.enabled"
              @click="setDefault(provider.id)"
              class="px-3 py-1.5 text-xs border border-luciano-border/50 rounded-lg hover:bg-luciano-border/20 transition-colors"
            >
              设为默认
            </button>
            <!-- 启用/禁用 -->
            <button
              v-if="!provider.isDefault"
              @click="toggleEnabled(provider)"
              class="px-3 py-1.5 text-xs border border-luciano-border/50 rounded-lg transition-colors"
              :class="provider.enabled ? 'hover:bg-red-500/10 hover:text-red-400 hover:border-red-400/30' : 'hover:bg-green-500/10 hover:text-green-400 hover:border-green-400/30'"
            >
              {{ provider.enabled ? '禁用' : '启用' }}
            </button>
            <!-- 编辑 -->
            <button
              @click="editProvider(provider)"
              class="px-3 py-1.5 text-xs border border-luciano-border/50 rounded-lg hover:bg-luciano-border/20 transition-colors"
            >
              编辑
            </button>
            <!-- 删除 -->
            <button
              v-if="!provider.isDefault"
              @click="deleteProvider(provider)"
              class="px-3 py-1.5 text-xs border border-red-400/30 rounded-lg text-red-400 hover:bg-red-500/10 transition-colors"
            >
              删除
            </button>
          </div>
        </div>

        <!-- 配置详情（展开） -->
        <div class="px-5 pb-4 border-t border-luciano-border/10 pt-3">
          <div class="grid grid-cols-2 gap-x-6 gap-y-2 text-xs">
            <template v-for="(value, key) in provider.config" :key="key">
              <div class="text-luciano-muted">{{ configLabel(key as string) }}</div>
              <div class="font-mono break-all" :class="key === 'secretAccessKey' ? 'text-luciano-muted' : ''">
                {{ key === 'secretAccessKey' ? '••••••••' : value }}
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showAddForm || editingProvider" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" @click.self="closeForm">
          <div class="bg-luciano-card border border-luciano-border/50 rounded-2xl shadow-2xl w-full max-w-lg mx-4 max-h-[85vh] overflow-y-auto">
            <!-- 弹窗标题 -->
            <div class="px-6 py-4 border-b border-luciano-border/30 flex items-center justify-between sticky top-0 bg-luciano-card z-10">
              <h2 class="font-semibold">{{ editingProvider ? '编辑存储配置' : '新增存储配置' }}</h2>
              <button @click="closeForm" class="w-8 h-8 rounded-lg hover:bg-luciano-border/20 flex items-center justify-center text-luciano-muted transition-colors">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" x2="6" y1="6" y2="18"/><line x1="6" x2="18" y1="6" y2="18"/></svg>
              </button>
            </div>

            <div class="px-6 py-4 space-y-4">
              <!-- 类型选择 -->
              <div>
                <label class="block text-xs text-luciano-muted mb-1.5">存储类型</label>
                <select
                  v-model="formData.providerType"
                  :disabled="!!editingProvider"
                  class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors disabled:opacity-50"
                >
                  <option value="local">本地存储</option>
                  <option value="s3">S3 兼容对象存储（TOS/MinIO/AWS）</option>
                </select>
              </div>

              <!-- 名称 -->
              <div>
                <label class="block text-xs text-luciano-muted mb-1.5">配置名称</label>
                <input
                  v-model="formData.name"
                  type="text"
                  placeholder="如：生产环境 TOS、本地开发"
                  class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
                />
              </div>

              <!-- 本地存储配置 -->
              <template v-if="formData.providerType === 'local'">
                <div>
                  <label class="block text-xs text-luciano-muted mb-1.5">存储路径</label>
                  <input v-model="formData.config.path" type="text" placeholder="/path/to/uploads" class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                </div>
                <div>
                  <label class="block text-xs text-luciano-muted mb-1.5">公网访问地址</label>
                  <input v-model="formData.config.publicUrl" type="text" placeholder="http://localhost:8090" class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                </div>
              </template>

              <!-- S3 配置 -->
              <template v-if="formData.providerType === 's3'">
                <div>
                  <label class="block text-xs text-luciano-muted mb-1.5">Endpoint</label>
                  <input v-model="formData.config.endpoint" type="text" placeholder="tos-s3-cn-beijing.volces.com" class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                  <p class="text-[10px] text-luciano-muted/60 mt-1">MinIO 示例：localhost:9000</p>
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-xs text-luciano-muted mb-1.5">Region</label>
                    <input v-model="formData.config.region" type="text" placeholder="cn-beijing" class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                  </div>
                  <div>
                    <label class="block text-xs text-luciano-muted mb-1.5">Bucket</label>
                    <input v-model="formData.config.bucket" type="text" placeholder="my-bucket" class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                  </div>
                </div>
                <div>
                  <label class="block text-xs text-luciano-muted mb-1.5">Access Key ID</label>
                  <input v-model="formData.config.accessKeyId" type="text" placeholder="AKLT..." class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                </div>
                <div>
                  <label class="block text-xs text-luciano-muted mb-1.5">Secret Access Key</label>
                  <input v-model="formData.config.secretAccessKey" type="password" placeholder="••••••••" class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                </div>
                <div>
                  <label class="block text-xs text-luciano-muted mb-1.5">公网访问地址</label>
                  <input v-model="formData.config.publicUrl" type="text" placeholder="https://bucket.tos-cn-beijing.volces.com" class="w-full px-3 py-2.5 rounded-lg bg-luciano-bg border border-luciano-border/50 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors" />
                  <p class="text-[10px] text-luciano-muted/60 mt-1">用于拼接文件公网访问 URL</p>
                </div>
                <div class="flex items-center gap-2">
                  <input v-model="formData.config.pathStyle" type="checkbox" id="pathStyle" class="w-4 h-4 rounded accent-apple-blue" />
                  <label for="pathStyle" class="text-xs text-luciano-muted">路径风格访问（MinIO 勾选，TOS/AWS 不勾选）</label>
                </div>
              </template>
            </div>

            <!-- 按钮 -->
            <div class="px-6 py-4 border-t border-luciano-border/30 flex items-center justify-end gap-3 sticky bottom-0 bg-luciano-card">
              <button @click="closeForm" class="px-4 py-2 text-sm text-luciano-muted hover:text-luciano-text transition-colors">取消</button>
              <button @click="saveProvider" :disabled="saving" class="px-4 py-2 text-sm bg-apple-blue text-white rounded-lg hover:bg-apple-blue/90 transition-colors disabled:opacity-50">
                {{ saving ? '保存中...' : (editingProvider ? '更新' : '创建') }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Toast 提示 -->
    <Transition name="toast">
      <div v-if="toast.show" class="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 rounded-xl shadow-lg text-sm font-medium"
        :class="toast.type === 'success' ? 'bg-green-500/90 text-white' : 'bg-red-500/90 text-white'">
        {{ toast.message }}
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
const api = useApi()

const loading = ref(true)
const providers = ref<any[]>([])
const showAddForm = ref(false)
const editingProvider = ref<any>(null)
const saving = ref(false)

const formData = ref<{
  providerType: string
  name: string
  config: Record<string, any>
}>({
  providerType: 's3',
  name: '',
  config: {},
})

const toast = ref({ show: false, message: '', type: 'success' as 'success' | 'error' })

const showToast = (message: string, type: 'success' | 'error' = 'success') => {
  toast.value = { show: true, message, type }
  setTimeout(() => { toast.value.show = false }, 2500)
}

// 类型图标
const providerIcon = (type: string) => {
  switch (type) {
    case 'local': return '📁'
    case 's3': return '☁️'
    default: return '💾'
  }
}

const providerIconClass = (type: string) => {
  switch (type) {
    case 'local': return 'bg-amber-500/10'
    case 's3': return 'bg-blue-500/10'
    default: return 'bg-purple-500/10'
  }
}

const providerTypeLabel = (type: string) => {
  switch (type) {
    case 'local': return '本地文件系统'
    case 's3': return 'S3 兼容对象存储'
    default: return type
  }
}

const configLabel = (key: string) => {
  const labels: Record<string, string> = {
    path: '存储路径',
    publicUrl: '公网地址',
    endpoint: 'Endpoint',
    region: 'Region',
    bucket: 'Bucket',
    accessKeyId: 'Access Key ID',
    secretAccessKey: 'Secret Key',
    pathStyle: '路径风格',
  }
  return labels[key] || key
}

// 加载配置列表
const loadProviders = async () => {
  loading.value = true
  try {
    const list = await api.rawRequest('/storage/providers')
    // rawRequest 返回原始响应
    providers.value = Array.isArray(list) ? list : []
  } catch (e: any) {
    showToast('加载失败：' + (e.message || '未知错误'), 'error')
    providers.value = []
  } finally {
    loading.value = false
  }
}

// 设为默认
const setDefault = async (id: number) => {
  try {
    await api.rawRequest(`/storage/providers/${id}/default`, { method: 'POST' })
    showToast('已切换默认存储')
    await loadProviders()
  } catch (e: any) {
    showToast('切换失败：' + (e.message || '未知错误'), 'error')
  }
}

// 启用/禁用
const toggleEnabled = async (provider: any) => {
  try {
    await api.rawRequest(`/storage/providers/${provider.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled: !provider.enabled }),
    })
    showToast(provider.enabled ? '已禁用' : '已启用')
    await loadProviders()
  } catch (e: any) {
    showToast('操作失败：' + (e.message || '未知错误'), 'error')
  }
}

// 编辑
const editProvider = (provider: any) => {
  editingProvider.value = provider
  formData.value = {
    providerType: provider.providerType,
    name: provider.name,
    config: { ...provider.config },
  }
  // pathStyle 可能是 boolean，转为 checkbox 值
  if (formData.value.config.pathStyle !== undefined) {
    formData.value.config.pathStyle = !!formData.value.config.pathStyle
  }
}

// 删除
const deleteProvider = async (provider: any) => {
  if (!confirm(`确定删除「${provider.name}」？`)) return
  try {
    await api.rawRequest(`/storage/providers/${provider.id}`, { method: 'DELETE' })
    showToast('已删除')
    await loadProviders()
  } catch (e: any) {
    showToast('删除失败：' + (e.message || '未知错误'), 'error')
  }
}

// 保存
const saveProvider = async () => {
  if (!formData.value.name.trim()) {
    showToast('请填写配置名称', 'error')
    return
  }

  saving.value = true
  try {
    if (editingProvider.value) {
      // 更新
      await api.rawRequest(`/storage/providers/${editingProvider.value.id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          name: formData.value.name,
          config: formData.value.config,
        }),
      })
      showToast('配置已更新')
    } else {
      // 新增
      await api.rawRequest('/storage/providers', {
        method: 'POST',
        body: JSON.stringify({
          providerType: formData.value.providerType,
          name: formData.value.name,
          config: formData.value.config,
        }),
      })
      showToast('配置已创建')
    }
    closeForm()
    await loadProviders()
  } catch (e: any) {
    showToast('保存失败：' + (e.message || '未知错误'), 'error')
  } finally {
    saving.value = false
  }
}

const closeForm = () => {
  showAddForm.value = false
  editingProvider.value = null
  formData.value = { providerType: 's3', name: '', config: {} }
}

onMounted(loadProviders)
</script>

<style scoped>
.modal-enter-active { transition: all 0.2s ease-out; }
.modal-leave-active { transition: all 0.15s ease-in; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-from .bg-luciano-card, .modal-leave-to .bg-luciano-card { transform: scale(0.95); }

.toast-enter-active { transition: all 0.2s ease-out; }
.toast-leave-active { transition: all 0.15s ease-in; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, 10px); }
</style>