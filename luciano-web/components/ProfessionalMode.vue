<!-- 专业模式 — 工作流管理 + 画布入口 -->
<template>
  <div class="flex flex-col h-full relative">
    <!-- Flow 画布模式 -->
    <FlowCanvas
      v-if="showFlowCanvas"
      :key="activeWorkflowId || 'new'"
      :project-id="projectId"
      :workflow-id="activeWorkflowId"
      @back="handleCanvasBack"
      @saved="handleWorkflowSaved"
    />

    <!-- 专业模式首页 -->
    <div v-else class="flex flex-col h-full">
      <!-- 未登录遮罩 -->
      <div v-if="requireAuth" class="absolute inset-0 z-10 bg-luciano-bg/80 backdrop-blur-sm flex flex-col items-center justify-center rounded-xl">
        <div class="text-center">
          <div class="text-4xl mb-3">🔒</div>
          <p class="text-sm text-luciano-muted mb-4">登录后使用专业模式</p>
          <button @click="navigateTo('/login')" class="px-5 py-2 bg-apple-blue text-white text-sm rounded-xl hover:bg-apple-blue/90 transition-colors">去登录</button>
        </div>
      </div>

      <!-- 头部 -->
      <div class="px-6 pt-6 pb-4">
        <h2 class="text-xl font-semibold mb-1">专业模式</h2>
        <p class="text-sm text-luciano-muted">精细控制每个创作环节</p>
      </div>

      <!-- 工作流区域 -->
      <div class="flex-1 overflow-y-auto px-6 pb-6">
        <!-- 新建工作流 -->
        <div class="flex items-center gap-3 mb-6">
          <button
            @click="handleCreateBlank"
            class="flex items-center gap-2 px-4 py-2.5 bg-apple-blue text-white text-sm font-medium rounded-xl hover:bg-apple-blue/90 active:scale-95 transition-all"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
            新建空白工作流
          </button>
          <button
            @click="showTemplatePanel = true"
            class="flex items-center gap-2 px-4 py-2.5 bg-luciano-card border border-luciano-border/50 text-sm font-medium rounded-xl hover:border-apple-blue/40 transition-all"
          >
            <span>📋</span>
            从模板创建
          </button>
        </div>

        <!-- 工作流列表 -->
        <div v-if="loadingWorkflows" class="flex items-center justify-center py-12 text-sm text-luciano-muted">
          加载中...
        </div>

        <div v-else-if="workflows.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
          <div class="text-5xl mb-4">🧩</div>
          <h3 class="text-base font-medium mb-2">还没有工作流</h3>
          <p class="text-sm text-luciano-muted mb-4">创建空白工作流或从模板开始</p>
        </div>

        <div v-else class="space-y-2">
          <div
            v-for="wf in workflows"
            :key="wf.id"
            @click="handleOpenWorkflow(wf)"
            class="flex items-center gap-4 p-4 bg-luciano-card rounded-xl border border-luciano-border/20 hover:border-apple-blue/40 cursor-pointer transition-all group"
          >
            <!-- 缩略图 / 图标 -->
            <div class="w-10 h-10 rounded-lg bg-luciano-bg flex items-center justify-center text-lg flex-shrink-0">
              {{ getCategoryIcon(wf.category) }}
            </div>
            <!-- 信息 -->
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium truncate">{{ wf.name }}</div>
              <div class="text-[10px] text-luciano-muted mt-0.5 flex items-center gap-2">
                <span v-if="wf.status" class="px-1.5 py-0.5 rounded text-[9px] font-medium" :class="statusClass(wf.status)">
                  {{ statusLabel(wf.status) }}
                </span>
                <span>{{ formatDate(wf.updatedAt) }}</span>
                <span v-if="wf.nodes">· {{ parseNodeCount(wf.nodes) }} 个节点</span>
              </div>
            </div>
            <!-- 操作 -->
            <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button @click.stop="handleDeleteWorkflow(wf)" class="p-1.5 rounded-lg hover:bg-red-500/10 text-luciano-muted hover:text-red-400 transition-colors" title="删除">
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
              </button>
            </div>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-luciano-muted group-hover:text-apple-blue group-hover:translate-x-0.5 transition-all flex-shrink-0"><path d="m9 18 6-6-6-6"/></svg>
          </div>
        </div>

        <!-- 创作要素 -->
        <div class="mt-8 pt-6 border-t border-luciano-border/20">
          <h3 class="text-xs text-luciano-muted uppercase tracking-wider mb-3">创作要素</h3>
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <NuxtLink
              v-for="item in items"
              :key="item.key"
              :to="item.to"
              class="flex flex-col items-center gap-1.5 p-3 bg-luciano-card rounded-xl border border-luciano-border/20 hover:border-luciano-border transition-all"
            >
              <span class="text-xl">{{ item.icon }}</span>
              <span class="text-xs text-luciano-muted">{{ item.label }}</span>
            </NuxtLink>
          </div>
        </div>
      </div>
    </div>

    <!-- 模板选择面板 -->
    <Teleport to="body">
      <div v-if="showTemplatePanel" class="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-center justify-center" @click.self="showTemplatePanel = false">
        <div class="bg-luciano-card rounded-2xl p-6 w-full max-w-md border border-luciano-border/50 shadow-2xl max-h-[80vh] overflow-y-auto">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold">选择模板</h3>
            <button @click="showTemplatePanel = false" class="text-luciano-muted hover:text-luciano-text transition-colors">✕</button>
          </div>
          <div v-if="loadingTemplates" class="py-8 text-center text-sm text-luciano-muted">加载中...</div>
          <div v-else-if="templates.length === 0" class="py-8 text-center">
            <p class="text-sm text-luciano-muted">暂无系统模板</p>
            <p class="text-xs text-luciano-muted/60 mt-2">你可以先创建空白工作流，然后保存为模板</p>
          </div>
          <div v-else class="space-y-2">
            <button
              v-for="tpl in templates"
              :key="tpl.id"
              @click="handleInstantiateTemplate(tpl)"
              class="w-full flex items-center gap-3 p-3 rounded-xl border border-luciano-border/30 hover:border-apple-blue/40 hover:bg-apple-blue/5 transition-all text-left"
            >
              <div class="w-10 h-10 rounded-lg bg-luciano-bg flex items-center justify-center text-lg flex-shrink-0">
                {{ getCategoryIcon(tpl.category) }}
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-medium truncate">{{ tpl.name }}</div>
                <div class="text-[10px] text-luciano-muted mt-0.5">
                  {{ tpl.category || '通用' }}
                  <span v-if="tpl.nodes">· {{ parseNodeCount(tpl.nodes) }} 个节点</span>
                </div>
              </div>
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-luciano-muted flex-shrink-0"><path d="m9 18 6-6-6-6"/></svg>
            </button>
          </div>
        </div>
      </div>

      <!-- 删除确认 -->
      <div v-if="deletingWorkflow" class="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-center justify-center" @click.self="deletingWorkflow = null">
        <div class="bg-luciano-card rounded-2xl p-6 w-full max-w-sm border border-luciano-border/50 shadow-2xl">
          <h3 class="text-lg font-semibold mb-2">删除工作流</h3>
          <p class="text-sm text-luciano-muted mb-4">确定删除「{{ deletingWorkflow.name }}」？此操作不可撤销。</p>
          <div class="flex gap-3">
            <button @click="deletingWorkflow = null" class="flex-1 px-4 py-2 text-sm rounded-lg border border-luciano-border/50 hover:bg-luciano-border/20 transition-colors">取消</button>
            <button @click="confirmDelete" class="flex-1 px-4 py-2 text-sm rounded-lg bg-red-500 text-white hover:bg-red-600 active:scale-95 transition-all">删除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import type { Workflow } from '~/types/flow'
import { ref, computed, watch, onMounted, nextTick } from 'vue'

const props = defineProps<{
  projectId: number
  requireAuth?: boolean
}>()

const flowApi = useFlowApi()
const { user } = useAuth()

const showFlowCanvas = ref(false)
const activeWorkflowId = ref<number | null>(null)

// 工作流列表
const workflows = ref<Workflow[]>([])
const loadingWorkflows = ref(true)

// 模板
const templates = ref<Workflow[]>([])
const loadingTemplates = ref(false)
const showTemplatePanel = ref(false)

// 删除
const deletingWorkflow = ref<Workflow | null>(null)

const items = [
  { key: 'characters', icon: '👤', label: '角色', to: `/projects/${props.projectId}/characters` },
  { key: 'scenes', icon: '🏞️', label: '场景', to: `/projects/${props.projectId}/scenes` },
  { key: 'props', icon: '📦', label: '道具', to: `/projects/${props.projectId}/props` },
  { key: 'storyboards', icon: '🎬', label: '分镜', to: `/projects/${props.projectId}/storyboards` },
]

// 加载工作流列表
async function loadWorkflows() {
  loadingWorkflows.value = true
  try {
    workflows.value = await flowApi.listByProject(props.projectId) || []
  } catch (e) {
    console.error('加载工作流列表失败:', e)
    workflows.value = []
  } finally {
    loadingWorkflows.value = false
  }
}

// 加载模板列表
async function loadTemplates() {
  loadingTemplates.value = true
  try {
    templates.value = await flowApi.listTemplates() || []
  } catch (e) {
    console.error('加载模板列表失败:', e)
    templates.value = []
  } finally {
    loadingTemplates.value = false
  }
}

// 新建空白工作流
async function handleCreateBlank() {
  try {
    console.log('[ProfessionalMode] Creating blank workflow...')
    const wf = await flowApi.createWorkflow({
      name: '未命名工作流',
      nodes: '[]',
      edges: '[]',
      variables: '{}',
      projectId: props.projectId,
      isTemplate: false,
      status: 'draft',
    })
    console.log('[ProfessionalMode] Create response:', wf, 'id:', wf?.id)
    activeWorkflowId.value = wf.id
    showFlowCanvas.value = true
    await loadWorkflows()
  } catch (e) {
    console.error('创建工作流失败:', e)
  }
}

// 从模板创建
async function handleInstantiateTemplate(tpl: Workflow) {
  const userId = user.value?.id
  if (!userId) return

  try {
    const instance = await flowApi.instantiateFromTemplate(tpl.id, userId, props.projectId, tpl.name + ' (副本)')
    activeWorkflowId.value = instance.id
    showFlowCanvas.value = true
    showTemplatePanel.value = false
    await loadWorkflows()
  } catch (e) {
    console.error('从模板创建失败:', e)
  }
}

// 打开已有工作流
function handleOpenWorkflow(wf: Workflow) {
  activeWorkflowId.value = wf.id
  showFlowCanvas.value = true
}

// 画布返回
async function handleCanvasBack() {
  showFlowCanvas.value = false
  await nextTick()
  activeWorkflowId.value = null
  loadWorkflows()
}

// 画布保存回调（用于刷新列表数据）
function handleWorkflowSaved(workflowId: number) {
  // 保存后刷新列表（标题可能变了）
  loadWorkflows()
}

// 删除工作流
async function confirmDelete() {
  if (!deletingWorkflow.value) return
  try {
    await flowApi.deleteWorkflow(deletingWorkflow.value.id)
    await loadWorkflows()
  } catch (e) {
    console.error('删除工作流失败:', e)
  }
  deletingWorkflow.value = null
}

function handleDeleteWorkflow(wf: Workflow) {
  deletingWorkflow.value = wf
}

// 工具函数
function getCategoryIcon(category?: string | null): string {
  const icons: Record<string, string> = {
    'video_generation': '🎬',
    'image_generation': '🖼️',
    'character_consistency': '👤',
    'style_transfer': '🎨',
    'lip_sync': '🗣️',
    'motion_control': '🎥',
  }
  return category ? (icons[category] || '🧩') : '🧩'
}

function statusClass(status: string): string {
  switch (status) {
    case 'draft': return 'bg-gray-500/15 text-gray-400'
    case 'running': return 'bg-amber-500/15 text-amber-400'
    case 'completed': return 'bg-emerald-500/15 text-emerald-400'
    case 'failed': return 'bg-red-500/15 text-red-400'
    default: return 'bg-gray-500/15 text-gray-400'
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'draft': return '草稿'
    case 'running': return '运行中'
    case 'completed': return '已完成'
    case 'failed': return '失败'
    default: return status
  }
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function parseNodeCount(nodesJson: string): number {
  try {
    const arr = JSON.parse(nodesJson)
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
}

// 打开模板面板时加载数据
watch(showTemplatePanel, (v) => {
  if (v) loadTemplates()
})

onMounted(() => {
  loadWorkflows()
})
</script>