<!-- 管理后台 — 操作日志 -->
<template>
  <div class="h-[calc(100vh-48px)] flex flex-col overflow-hidden">
    <!-- 顶部统计卡片 -->
    <div class="px-6 py-4 border-b border-luciano-border/30 shrink-0">
      <h1 class="text-lg font-semibold mb-3">📊 操作日志</h1>
      <div v-if="stats" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
        <div class="bg-luciano-card rounded-lg px-3 py-2">
          <div class="text-xs text-luciano-muted">总调用</div>
          <div class="text-lg font-bold">{{ stats.totalCalls ?? 0 }}</div>
        </div>
        <div class="bg-luciano-card rounded-lg px-3 py-2">
          <div class="text-xs text-luciano-muted">提交</div>
          <div class="text-lg font-bold text-blue-400">{{ stats.submitCount ?? 0 }}</div>
        </div>
        <div class="bg-luciano-card rounded-lg px-3 py-2">
          <div class="text-xs text-luciano-muted">轮询</div>
          <div class="text-lg font-bold text-green-400">{{ stats.pollCount ?? 0 }}</div>
        </div>
        <div class="bg-luciano-card rounded-lg px-3 py-2">
          <div class="text-xs text-luciano-muted">错误</div>
          <div class="text-lg font-bold" :class="(stats.errorCount ?? 0) > 0 ? 'text-red-400' : 'text-luciano-muted'">{{ stats.errorCount ?? 0 }}</div>
        </div>
        <div class="bg-luciano-card rounded-lg px-3 py-2">
          <div class="text-xs text-luciano-muted">平均耗时</div>
          <div class="text-lg font-bold">{{ Math.round(stats.avgDurationMs ?? 0) }}ms</div>
        </div>
        <div class="bg-luciano-card rounded-lg px-3 py-2">
          <div class="text-xs text-luciano-muted">总扣费</div>
          <div class="text-lg font-bold text-amber-400">{{ (stats.totalCredits ?? 0).toFixed(2) }}</div>
        </div>
      </div>
      <div v-else class="text-sm text-luciano-muted">加载统计中...</div>
    </div>

    <!-- 筛选区 -->
    <div class="px-6 py-3 flex items-center gap-3 border-b border-luciano-border/20 shrink-0 flex-wrap">
      <select v-model="filters.adapterId" class="bg-luciano-card border border-luciano-border/50 rounded-lg px-3 py-1.5 text-sm">
        <option value="">全部适配器</option>
        <option value="kling">可灵</option>
        <option value="seedance">Seedance</option>
      </select>
      <select v-model="filters.capability" class="bg-luciano-card border border-luciano-border/50 rounded-lg px-3 py-1.5 text-sm">
        <option value="">全部能力</option>
        <option value="text_to_video">文生视频</option>
        <option value="image_to_video">图生视频</option>
        <option value="first_last_frame">首尾帧</option>
        <option value="video_extend">视频续写</option>
        <option value="camera_control">运镜控制</option>
        <option value="lip_sync">对口型</option>
        <option value="omni_video">Omni视频</option>
        <option value="text_to_image">文生图</option>
        <option value="omni_image">Omni图片</option>
      </select>
      <select v-model="filters.operationType" class="bg-luciano-card border border-luciano-border/50 rounded-lg px-3 py-1.5 text-sm">
        <option value="">全部操作</option>
        <option value="submit">提交</option>
        <option value="poll">轮询</option>
        <option value="download">下载</option>
      </select>
      <select v-model="filters.responseStatus" class="bg-luciano-card border border-luciano-border/50 rounded-lg px-3 py-1.5 text-sm">
        <option value="">全部状态</option>
        <option value="200">200 成功</option>
        <option value="400">400 客户端错误</option>
        <option value="401">401 未授权</option>
        <option value="500">500 服务端错误</option>
      </select>
      <button @click="loadLogs(1)" class="px-4 py-1.5 bg-apple-blue text-white text-sm rounded-lg hover:bg-apple-blue/90 transition-colors">查询</button>
      <button @click="resetFilters" class="px-4 py-1.5 bg-luciano-card text-luciano-muted text-sm rounded-lg hover:bg-luciano-border/30 transition-colors">重置</button>
    </div>

    <!-- 日志表格 -->
    <div class="flex-1 overflow-auto px-6 py-3">
      <div v-if="loading" class="flex items-center justify-center h-32 text-luciano-muted">
        <svg class="animate-spin h-6 w-6 mr-2" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/></svg>
        加载中...
      </div>
      <div v-else-if="logs.length === 0" class="flex items-center justify-center h-32 text-luciano-muted">
        暂无日志数据
      </div>
      <table v-else class="w-full text-sm">
        <thead>
          <tr class="text-luciano-muted text-xs border-b border-luciano-border/30">
            <th class="text-left py-2 px-2">时间</th>
            <th class="text-left py-2 px-2">用户</th>
            <th class="text-left py-2 px-2">适配器</th>
            <th class="text-left py-2 px-2">能力</th>
            <th class="text-left py-2 px-2">操作</th>
            <th class="text-left py-2 px-2">状态</th>
            <th class="text-left py-2 px-2">耗时</th>
            <th class="text-left py-2 px-2">扣费</th>
            <th class="text-left py-2 px-2">TaskID</th>
            <th class="text-left py-2 px-2">详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id" class="border-b border-luciano-border/10 hover:bg-luciano-card/50 transition-colors">
            <td class="py-2 px-2 text-xs text-luciano-muted whitespace-nowrap">{{ formatTime(log.createdAt) }}</td>
            <td class="py-2 px-2">{{ log.username || log.userId || '-' }}</td>
            <td class="py-2 px-2">
              <span class="px-1.5 py-0.5 rounded text-xs font-medium"
                    :class="log.adapterId === 'kling' ? 'bg-blue-500/20 text-blue-400' : 'bg-green-500/20 text-green-400'">
                {{ log.adapterId }}
              </span>
            </td>
            <td class="py-2 px-2 text-xs">{{ formatCapability(log.capability) }}</td>
            <td class="py-2 px-2">
              <span class="px-1.5 py-0.5 rounded text-xs"
                    :class="{
                      'bg-yellow-500/20 text-yellow-400': log.operationType === 'submit',
                      'bg-blue-500/20 text-blue-400': log.operationType === 'poll',
                      'bg-green-500/20 text-green-400': log.operationType === 'download',
                    }">
                {{ log.operationType }}
              </span>
            </td>
            <td class="py-2 px-2">
              <span :class="log.responseStatus >= 400 ? 'text-red-400' : 'text-green-400'">{{ log.responseStatus }}</span>
            </td>
            <td class="py-2 px-2 text-xs">{{ log.durationMs ? log.durationMs + 'ms' : '-' }}</td>
            <td class="py-2 px-2 text-xs text-amber-400">{{ log.creditsUsed ?? '-' }}</td>
            <td class="py-2 px-2 text-xs max-w-[120px] truncate" :title="log.taskId || ''">{{ log.taskId ? log.taskId.split(':').pop() : '-' }}</td>
            <td class="py-2 px-2">
              <button @click="showDetail(log)" class="text-apple-blue text-xs hover:underline">查看</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="px-6 py-3 border-t border-luciano-border/30 flex items-center justify-between shrink-0">
      <span class="text-xs text-luciano-muted">共 {{ total }} 条</span>
      <div class="flex items-center gap-2">
        <button @click="loadLogs(currentPage - 1)" :disabled="currentPage <= 1"
                class="px-3 py-1 text-sm rounded-lg bg-luciano-card hover:bg-luciano-border/30 disabled:opacity-30 transition-colors">上一页</button>
        <span class="text-sm">{{ currentPage }} / {{ totalPages }}</span>
        <button @click="loadLogs(currentPage + 1)" :disabled="currentPage >= totalPages"
                class="px-3 py-1 text-sm rounded-lg bg-luciano-card hover:bg-luciano-border/30 disabled:opacity-30 transition-colors">下一页</button>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="detailLog" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="detailLog = null">
          <div class="bg-luciano-bg border border-luciano-border rounded-2xl shadow-2xl w-full max-w-2xl max-h-[80vh] overflow-auto mx-4">
            <div class="sticky top-0 bg-luciano-bg border-b border-luciano-border/30 px-6 py-4 flex items-center justify-between">
              <h2 class="text-base font-semibold">操作详情 #{{ detailLog.id }}</h2>
              <button @click="detailLog = null" class="text-luciano-muted hover:text-luciano-text transition-colors">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <div class="px-6 py-4 space-y-3">
              <div class="grid grid-cols-2 gap-3 text-sm">
                <div><span class="text-luciano-muted">用户：</span>{{ detailLog.username || detailLog.userId || '-' }}</div>
                <div><span class="text-luciano-muted">适配器：</span>{{ detailLog.adapterId }}</div>
                <div><span class="text-luciano-muted">能力：</span>{{ detailLog.capability }}</div>
                <div><span class="text-luciano-muted">操作：</span>{{ detailLog.operationType }}</div>
                <div><span class="text-luciano-muted">状态码：</span>{{ detailLog.responseStatus }}</div>
                <div><span class="text-luciano-muted">耗时：</span>{{ detailLog.durationMs ? detailLog.durationMs + 'ms' : '-' }}</div>
                <div><span class="text-luciano-muted">扣费：</span>{{ detailLog.creditsUsed ?? '-' }}</div>
                <div><span class="text-luciano-muted">时间：</span>{{ formatTime(detailLog.createdAt) }}</div>
              </div>
              <div v-if="detailLog.taskId" class="text-sm"><span class="text-luciano-muted">TaskID：</span><span class="font-mono text-xs">{{ detailLog.taskId }}</span></div>
              <div v-if="detailLog.providerTaskId" class="text-sm"><span class="text-luciano-muted">ProviderTaskID：</span><span class="font-mono text-xs">{{ detailLog.providerTaskId }}</span></div>
              <div v-if="detailLog.resultUrl" class="text-sm"><span class="text-luciano-muted">结果URL：</span><a :href="detailLog.resultUrl" target="_blank" class="text-apple-blue hover:underline text-xs break-all">{{ detailLog.resultUrl }}</a></div>
              <div v-if="detailLog.errorCode || detailLog.errorMessage" class="text-sm">
                <span class="text-luciano-muted">错误：</span>
                <span class="text-red-400">{{ detailLog.errorCode }}</span>
                <span v-if="detailLog.errorMessage" class="text-red-300 ml-1">{{ detailLog.errorMessage }}</span>
              </div>
              <div v-if="detailLog.method && detailLog.path" class="text-sm">
                <span class="text-luciano-muted">请求：</span>
                <span class="font-mono text-xs px-1.5 py-0.5 rounded bg-luciano-card">{{ detailLog.method }}</span>
                <span class="font-mono text-xs ml-1 break-all">{{ detailLog.path }}</span>
              </div>
              <div v-if="detailLog.requestBody">
                <div class="text-luciano-muted text-sm mb-1">请求体：</div>
                <pre class="bg-luciano-card rounded-lg p-3 text-xs overflow-auto max-h-48 font-mono">{{ formatJson(detailLog.requestBody) }}</pre>
              </div>
              <div v-if="detailLog.responseBody">
                <div class="text-luciano-muted text-sm mb-1">响应体：</div>
                <pre class="bg-luciano-card rounded-lg p-3 text-xs overflow-auto max-h-48 font-mono">{{ formatJson(detailLog.responseBody) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ layout: 'admin' })

const { getOperationLogs, getOperationLogStats } = useApi()

const loading = ref(false)
const logs = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const stats = ref<any>(null)
const detailLog = ref<any>(null)

const filters = reactive({
  adapterId: '',
  capability: '',
  operationType: '',
  responseStatus: '' as string | number,
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const capabilityMap: Record<string, string> = {
  text_to_video: '文生视频',
  image_to_video: '图生视频',
  first_last_frame: '首尾帧',
  video_extend: '视频续写',
  camera_control: '运镜控制',
  lip_sync: '对口型',
  omni_video: 'Omni视频',
  text_to_image: '文生图',
  omni_image: 'Omni图片',
  reference_to_video: '参考图生视频',
}

const formatTime = (t: string) => {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

const formatCapability = (c: string) => capabilityMap[c] || c

const formatJson = (s: string) => {
  if (!s) return ''
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

const loadLogs = async (page: number) => {
  loading.value = true
  try {
    const params: any = { page, size: pageSize }
    if (filters.adapterId) params.adapterId = filters.adapterId
    if (filters.capability) params.capability = filters.capability
    if (filters.operationType) params.operationType = filters.operationType
    if (filters.responseStatus) params.responseStatus = filters.responseStatus

    const data = await getOperationLogs(params)
    logs.value = data?.records || []
    total.value = data?.total || 0
    currentPage.value = page
  } catch (e) {
    console.error('[AdminLogs] Failed to load logs:', e)
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    stats.value = await getOperationLogStats()
  } catch (e) {
    console.error('[AdminLogs] Failed to load stats:', e)
  }
}

const resetFilters = () => {
  filters.adapterId = ''
  filters.capability = ''
  filters.operationType = ''
  filters.responseStatus = ''
  loadLogs(1)
}

const showDetail = (log: any) => {
  detailLog.value = log
}

onMounted(() => {
  loadLogs(1)
  loadStats()
})
</script>

<style scoped>
.modal-enter-active { transition: all 0.2s ease-out; }
.modal-leave-active { transition: all 0.15s ease-in; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>