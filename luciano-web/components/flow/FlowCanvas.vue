<!-- Flow 画布 — ComfyUI 风格暗色主题 -->
<template>
  <div ref="canvasWrapperRef" class="flow-canvas-wrapper" :class="isDark ? 'flow-dark' : 'flow-light'">
    <!-- 灵动岛（顶部悬浮工具栏） -->
    <div class="dynamic-island" :class="{ 'island-expanded': islandExpanded }">
      <!-- 顶栏：始终显示 -->
      <div class="island-bar">
        <button class="island-btn" @click="emit('back')" title="返回工作流列表">
          <svg class="island-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
          <span class="island-tooltip">返回</span>
        </button>
        <span class="island-divider"></span>
        <button class="island-btn island-name" @click="startEditName" title="点击编辑名称">
          {{ workflowName }}
          <svg class="island-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="opacity:0.35"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>
          <span class="island-tooltip">编辑名称</span>
        </button>
        <span class="island-divider"></span>
        <button class="island-btn" @click="handleSave" :disabled="isSaving" title="保存工作流">
          <span v-if="isSaving" class="island-spinner"></span>
          <svg v-else class="island-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8"/><path d="M7 3v5h8"/></svg>
          <span v-if="hasUnsavedChanges" class="island-dot"></span>
          <span class="island-tooltip">保存</span>
        </button>
        <button class="island-btn" @click="toggleIslandPanel" :class="{ 'island-btn-active': islandExpanded }" title="添加节点">
          <svg class="island-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v8"/><path d="M8 12h8"/></svg>
          <span class="island-tooltip">添加节点</span>
        </button>
        <span class="island-divider"></span>
        <button class="island-btn" @click="handleAssetLibrary" title="素材库">
          <svg class="island-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
          <span class="island-tooltip">素材库</span>
        </button>
        <button class="island-btn" @click="handleTemplates" title="模板">
          <svg class="island-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M3 9h18"/><path d="M9 21V9"/></svg>
          <span class="island-tooltip">模板</span>
        </button>
      </div>

      <!-- 展开区：岛内延伸 -->
      <transition name="island-grow">
        <div v-if="islandExpanded" class="island-body">
          <div class="island-separator"></div>
          <!-- 分类标签 -->
          <div class="island-tabs">
            <button
              v-for="cat in islandCategories"
              :key="cat"
              class="island-tab"
              :class="{ 'island-tab-active': islandSelectedCat === cat }"
              @click="islandSelectedCat = cat"
            >
              {{ cat }}
            </button>
          </div>
          <!-- 节点列表 -->
          <div class="island-node-list">
            <button
              v-for="item in islandCurrentItems"
              :key="item.type"
              class="island-node-item"
              @click="addNodeFromIsland(item)"
            >
              <span class="island-node-icon">{{ item.icon || '◆' }}</span>
              <span class="island-node-label">{{ item.displayName }}</span>
            </button>
          </div>
        </div>
      </transition>
    </div>

    <!-- 首次保存弹窗 -->
    <div v-if="showSaveDialog" class="save-dialog-overlay" @click.self="cancelSaveDialog">
      <div class="save-dialog">
        <h3>保存工作流</h3>
        <div class="save-dialog-field">
          <label>名称</label>
          <input
            ref="saveNameInput"
            v-model="saveDialogName"
            type="text"
            placeholder="输入工作流名称"
            maxlength="50"
            @keydown.enter="confirmSaveDialog"
          />
        </div>
        <div class="save-dialog-field">
          <label>备注</label>
          <textarea
            v-model="saveDialogDesc"
            placeholder="可选，描述工作流用途"
            rows="3"
            maxlength="200"
          />
        </div>
        <div class="save-dialog-actions">
          <button class="save-dialog-btn cancel" @click="cancelSaveDialog">取消</button>
          <button class="save-dialog-btn confirm" @click="confirmSaveDialog">保存</button>
        </div>
      </div>
    </div>

    <!-- 编辑名称/备注弹窗（点击标题触发） -->
    <div v-if="showNameEdit" class="save-dialog-overlay" @click.self="cancelNameEdit">
      <div class="save-dialog">
        <h3>编辑工作流信息</h3>
        <div class="save-dialog-field">
          <label>名称</label>
          <input
            ref="nameEditInput"
            v-model="nameEditValue"
            type="text"
            placeholder="输入工作流名称"
            maxlength="50"
            @keydown.enter="confirmNameEdit"
          />
        </div>
        <div class="save-dialog-field">
          <label>备注</label>
          <textarea
            v-model="nameEditDesc"
            placeholder="可选，描述工作流用途"
            rows="3"
            maxlength="200"
            @keydown.enter="confirmNameEdit"
          />
        </div>
        <div class="save-dialog-actions">
          <button class="save-dialog-btn cancel" @click="cancelNameEdit">取消</button>
          <button class="save-dialog-btn confirm" @click="confirmNameEdit">确认</button>
        </div>
      </div>
    </div>

    <!-- 画布区域 -->
    <div class="canvas-container">
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        :node-types="nodeTypes"
        :default-edge-options="defaultEdgeOptions"
        :fit-view-on-init="true"
        :snap-to-grid="snapToGrid"
        :snap-grid="[10, 10]"
        :min-zoom="0.2"
        :max-zoom="2"
        @connect="onConnect"
        @node-click="onNodeClick"
        @pane-click="onPaneClick"
        @viewport-change="syncZoomFromViewport"
        @node-context-menu="onNodeContextMenu"
        @pane-context-menu="onPaneContextMenu"
      >
        <Background :gap="20" :size="1.5" :color="isDark ? '#1e293b' : '#d1d5db'" variant="dots" />
        <MiniMap v-if="showMiniMap" position="bottom-left" :node-color="miniMapNodeColor" />
      </VueFlow>

      <!-- 画布控制面板（右下角） -->
      <div class="canvas-controls" :style="{ bottom: '12px' }">
        <!-- 关闭/开启小地图 -->
        <button class="ctrl-btn" @click="showMiniMap = !showMiniMap" :title="showMiniMap ? '关闭小地图' : '开启小地图'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <rect v-if="showMiniMap" x="13" y="13" width="8" height="8" rx="1" opacity="0.4" />
          </svg>
        </button>
        <!-- 网格吸附 -->
        <button class="ctrl-btn" :class="{ active: snapToGrid }" @click="toggleSnap" title="网格吸附">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 3h18v18H3z" />
            <path d="M3 9h18M3 15h18M9 3v18M15 3v18" stroke-dasharray="2 2" />
          </svg>
        </button>
        <!-- 缩放控制 -->
        <button class="ctrl-btn" @click="handleZoomOut" title="缩小">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /><line x1="8" y1="11" x2="14" y2="11" />
          </svg>
        </button>
        <div class="zoom-slider-wrap">
          <input type="range" class="zoom-slider" :min="20" :max="200" :value="zoomPercent" @input="handleZoomSlider" />
          <span class="zoom-label">{{ zoomPercent }}%</span>
        </div>
        <button class="ctrl-btn" @click="handleZoomIn" title="放大">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /><line x1="11" y1="8" x2="11" y2="14" /><line x1="8" y1="11" x2="14" y2="11" />
          </svg>
        </button>
      </div>

      <!-- 参数面板（浮动在画布右侧） -->
      <ParamPanel
        v-if="selectedNodeData"
        :visible="!!selectedNodeData"
        :node-data="selectedNodeData"
        :node-id="selectedNodeId || ''"
        :connected-inputs="connectedInputsForSelected"
        :image-nodes="canvasImageNodes"
        @close="clearSelection"
        @update:params="onUpdateParams"
        @update:adapter-id="onUpdateAdapterId"
      />
    </div>

    <!-- TODO: P3 模板体系上线后恢复全执行状态提示 -->
    <!-- <div v-if="isExecuting" class="execution-status">
      <span class="status-pulse"></span>
      执行中 {{ completedCount }}/{{ totalCount }}...
    </div> -->

    <!-- 缩略图右键菜单 -->
    <div v-if="thumbContextMenu.visible" class="thumb-context-menu"
      :style="{ left: thumbContextMenu.x + 'px', top: thumbContextMenu.y + 'px' }"
      @click.stop
    >
      <button class="thumb-menu-btn" @click="confirmCreateRefNode">
        🔗 创建引用节点 #{{ thumbContextMenu.imageIndex + 1 }}
      </button>
    </div>

    <!-- 右键菜单 -->
    <ContextMenu
      :visible="contextMenu.visible"
      :position="contextMenu.position"
      :type="contextMenu.type"
      :has-image-output="contextMenuHasImageOutput"
      :can-execute="contextMenuCanExecute"
      :node-status="contextMenuNodeStatus"
      @execute-node="handleExecuteNode"
      @become-element="handleBecomeElement"
      @duplicate="handleDuplicateNode"
      @duplicate-with-edges="handleDuplicateWithEdges"
      @copy="handleCopyNodeParams"
      @delete="handleDeleteNode"
      @add-node="openAddPanelFromContext"
      @fit-view="handleFitView(); closeContextMenu()"
      @select-all="handleSelectAll"
    />

    <!-- 成为主体弹窗 -->
    <div v-if="elementDialog.visible" class="element-dialog-overlay" @click.self="elementDialog.visible = false">
      <div class="element-dialog">
        <h3>🧑 成为主体</h3>
        <div class="dialog-source-info">
          <img v-if="elementDialog.sourceThumb" :src="elementDialog.sourceThumb" class="dialog-source-thumb" />
          <span>来源：{{ elementDialog.sourceNodeName }}</span>
        </div>
        <div class="dialog-field">
          <label>主体名称 <span class="required-star">*</span></label>
          <input v-model="elementDialog.elementName" type="text" placeholder="输入主体名称" class="dialog-input" />
        </div>
        <div class="dialog-field">
          <label>描述</label>
          <input v-model="elementDialog.elementDesc" type="text" placeholder="可选描述" class="dialog-input" />
        </div>
        <div class="dialog-field">
          <label>标签</label>
          <select v-model="elementDialog.tagId" class="dialog-input">
            <option value="o_102">人物</option>
            <option value="o_103">动物</option>
            <option value="o_104">道具</option>
            <option value="o_105">服饰</option>
            <option value="o_106">场景</option>
            <option value="o_107">特效</option>
            <option value="o_101">热梗</option>
            <option value="o_108">其他</option>
          </select>
        </div>
        <div class="dialog-hint">💡 系统将自动生成参考图并创建主体</div>
        <div class="dialog-actions">
          <button class="dialog-cancel" @click="elementDialog.visible = false">取消</button>
          <button class="dialog-confirm" :disabled="elementDialog.creating" @click="confirmBecomeElement">
            {{ elementDialog.creating ? '创建中...' : '创建主体' }}
          </button>
        </div>
        <div v-if="elementDialog.statusMsg" class="dialog-status" :class="elementDialog.statusType">{{ elementDialog.statusMsg }}</div>
      </div>
    </div>

    <!-- 添加节点面板 -->
    <div v-if="showAddPanel" class="add-panel-overlay" @click.self="showAddPanel = false">
      <div class="add-panel">
        <div class="add-panel-header">
          <h3>添加节点</h3>
          <button class="close-btn" @click="showAddPanel = false">✕</button>
        </div>
        <input
          v-model="searchQuery"
          class="search-input"
          placeholder="搜索节点..."
          autofocus
        />
        <div class="add-panel-categories">
          <div v-for="(items, cat) in categorizedSchemas" :key="cat" class="category-group">
            <h4 class="category-title">{{ cat }}</h4>
            <div class="category-items">
              <button
                v-for="item in items"
                :key="item.type"
                class="add-item-btn"
                @click="addNodeFromSchema(item)"
              >
                <span class="add-item-icon">{{ item.icon }}</span>
                {{ item.displayName }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, markRaw, onMounted, onBeforeUnmount, onUnmounted, nextTick } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
// import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import type { Connection } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import CapabilityNode from './CapabilityNode.vue'
import SpecialNode from './SpecialNode.vue'
import ReferenceNode from './ReferenceNode.vue'
import ParamPanel from './ParamPanel.vue'
import ContextMenu from './ContextMenu.vue'
import {
  type FlowNodeData,
  serializeToWorkflow,
  createNode,
  createEdge,
  deserializeWorkflow,
  miniMapNodeColor,
} from '~/composables/useFlowCanvas'
import { useFlowApi } from '~/composables/useFlowApi'
import { useApi } from '~/composables/useApi'
import { useMediaLoader } from '~/composables/useMediaLoader'
import type { NodeTypeSchema, PortTypeCode } from '~/types/flow'
import { PORT_TYPE_COLORS, PORT_TYPE_NAMES, canConnectPorts } from '~/types/flow'

const { isDark } = useTheme()

const props = defineProps<{
  projectId: number
  workflowId?: number
}>()

const emit = defineEmits<{
  back: []
  saved: [workflowId: number]
  executed: [executionId: number]
}>()

const {
  nodes, edges, addNodes, addEdges, removeNodes, removeEdges, findNode, fitView, getSelectedNodes, screenToFlowCoordinate,
  zoomIn, zoomOut, zoomTo, getViewport, updateNodeInternals, setNodes, setEdges,
} = useVueFlow({
  id: 'flow-canvas',
})

const flowApi = useFlowApi()
const api = useApi()
const mediaLoader = useMediaLoader()

const nodeTypes = {
  capability: markRaw(CapabilityNode),
  special: markRaw(SpecialNode),
  reference: markRaw(ReferenceNode),
}

const defaultEdgeOptions = computed(() => ({
  type: 'default' as const,
  animated: true,
  style: { stroke: isDark.value ? '#475569' : '#94a3b8', strokeWidth: 2 },
}))

// 状态
const showAddPanel = ref(false)

// 灵动岛展开面板
const islandExpanded = ref(false)
const islandSelectedCat = ref('图片')
const islandCategories = computed(() => {
  const cats = new Set<string>()
  for (const schema of nodeTypeList.value) {
    cats.add(schema.category || '其他')
  }
  return Array.from(cats)
})
const islandCurrentItems = computed(() => {
  return nodeTypeList.value.filter(s => (s.category || '其他') === islandSelectedCat.value)
})

function toggleIslandPanel() {
  islandExpanded.value = !islandExpanded.value
}

function addNodeFromIsland(schema: any) {
  let position: { x: number; y: number }
  if (addPanelFlowPos.value.x >= 0 && addPanelFlowPos.value.y >= 0) {
    position = {
      x: addPanelFlowPos.value.x - 40,
      y: addPanelFlowPos.value.y - 20,
    }
  } else {
    position = {
      x: 200 + Math.random() * 200,
      y: 200 + Math.random() * 200,
    }
  }
  const node = createNode(schema, position)
  addNodes([node])
  nextTick(() => {
    updateNodeInternals(node.id)
  })
  islandExpanded.value = false
}
const searchQuery = ref('')
const addPanelFlowPos = ref({ x: 0, y: 0 })  // 右键时新节点放置的画布坐标
const workflowName = ref('未命名工作流')
const workflowDesc = ref('')
const isExecuting = ref(false)
const isSaving = ref(false)
const hasUnsavedChanges = ref(false)
const completedCount = ref(0)
const totalCount = ref(0)

// 首次保存弹窗
const showSaveDialog = ref(false)
const saveDialogName = ref('')
const saveDialogDesc = ref('')
const saveNameInput = ref<HTMLInputElement | null>(null)

// 内联名称编辑
const showNameEdit = ref(false)
const nameEditValue = ref('')
const nameEditDesc = ref('')
const nameEditInput = ref<HTMLInputElement | null>(null)

// 画布控制
const showMiniMap = ref(true)
const snapToGrid = ref(true)
const zoomPercent = ref(100)

// 同步 viewport zoom 到 slider
function syncZoomFromViewport() {
  const vp = getViewport()
  zoomPercent.value = Math.round(vp.zoom * 100)
}

function handleZoomIn() {
  zoomIn()
  syncZoomFromViewport()
}

function handleZoomOut() {
  zoomOut()
  syncZoomFromViewport()
}

function handleZoomSlider(e: Event) {
  const val = Number((e.target as HTMLInputElement).value)
  zoomPercent.value = val
  zoomTo(val / 100)
}

function toggleSnap() {
  snapToGrid.value = !snapToGrid.value
}
const canvasWrapperRef = ref<HTMLElement | null>(null)
const selectedNodeId = ref<string | null>(null)
// 缩略图右键菜单
const thumbContextMenu = ref<{ visible: boolean; x: number; y: number; parentNodeId: string; imageIndex: number }>({
  visible: false, x: 0, y: 0, parentNodeId: '', imageIndex: 0
})
const selectedNodeData = ref<FlowNodeData | null>(null)

// 计算当前选中节点的连入端口
const connectedInputsForSelected = computed(() => {
  if (!selectedNodeId.value) return []
  return edges.value
    .filter(e => e.target === selectedNodeId.value)
    .map(e => ({
      sourceNodeId: e.source,
      sourceSlot: e.sourceHandle || '',
      targetSlot: e.targetHandle || '',
      sourceNode: findNode(e.source)?.data as FlowNodeData | undefined,
    }))
})

// 画布上所有有图片输出的节点（供“从画布选择”使用）
const canvasImageNodes = computed(() => {
  return nodes.value
    .filter(n => {
      const data = n.data as FlowNodeData
      // 只显示单图输出的节点（multi=false 的 image 端口）
      // 多图端口（Omni Image 等）不能作为主体正面图
      const singleImageSlots = (data.outputSlots || []).filter((s: any) => s.dataType === 'image' && !s.multi)
      return singleImageSlots.length > 0
    })
    .map(n => {
      const data = n.data as FlowNodeData
      const urls = data.outputUrls || (data.outputUrl ? [data.outputUrl] : [])
      return {
        nodeId: n.id,
        nodeName: data.label || data.type,
        nodeType: data.type,
        imageUrls: urls,
        mediaIds: urls
          .map((u: string) => { const m = u.match(/\/media\/(\d+)\/file/); return m ? parseInt(m[1]) : null })
          .filter((id: number | null) => id !== null) as number[],
      }
    })
    .filter(n => n.imageUrls.length > 0)
})
const currentWorkflowId = ref<number | null>(props.workflowId || null)
const contextMenu = ref<{
  visible: boolean
  position: { x: number; y: number }
  type: 'node' | 'pane'
  nodeId?: string
}>({ visible: false, position: { x: 0, y: 0 }, type: 'pane' })

// 当前右键的节点是否有图片输出端口（决定是否显示“成为主体”）
const contextMenuHasImageOutput = computed(() => {
  if (!contextMenu.value.nodeId) return false
  const node = findNode(contextMenu.value.nodeId)
  if (!node) return false
  const data = node.data as FlowNodeData
  // 只有单图输出端口（multi=false）才能成为主体
  // 多图端口（如 Omni Image）不行，一个主体只能对应一张图
  const singleImageSlots = (data.outputSlots || []).filter(s => s.dataType === 'image' && !s.multi)
  if (singleImageSlots.length === 0) return false
  // 还要确认节点实际只产出了一张图（outputUrls 只有0或1张）
  const urls = data.outputUrls || (data.outputUrl ? [data.outputUrl] : [])
  return urls.length <= 1
})

// 当前右键的节点状态
const contextMenuNodeStatus = computed(() => {
  if (!contextMenu.value.nodeId) return ''
  const node = findNode(contextMenu.value.nodeId)
  if (!node) return ''
  return (node.data as FlowNodeData).status || ''
})

// 当前右键的节点是否可以执行
const contextMenuCanExecute = computed(() => {
  if (!contextMenu.value.nodeId) return true
  const node = findNode(contextMenu.value.nodeId)
  if (!node) return true
  const data = node.data as FlowNodeData
  const status = data.status || ''
  // completed 状态不允许执行
  if (status === 'completed') return false
  // running 状态不允许执行
  if (status === 'running') return false
  // failed 和 idle 状态可以执行
  return true
})

// 成为主体弹窗状态
const elementDialog = ref<{
  visible: boolean
  sourceNodeId: string
  sourceNodeName: string
  sourceThumb: string
  elementName: string
  elementDesc: string
  tagId: string
  creating: boolean
  statusMsg: string
  statusType: string
}>({
  visible: false,
  sourceNodeId: '',
  sourceNodeName: '',
  sourceThumb: '',
  elementName: '',
  elementDesc: '',
  tagId: 'o_102',
  creating: false,
  statusMsg: '',
  statusType: '',
})
let eventSource: EventSource | null = null

// 节点选择
function onNodeClick({ node }: any) {
  selectedNodeId.value = node.id
  // 深拷贝 data，确保 ParamPanel 拿到新对象而不是旧引用
  selectedNodeData.value = JSON.parse(JSON.stringify(node.data)) as FlowNodeData
  closeContextMenu()
}

function onPaneClick() {
  selectedNodeId.value = null
  selectedNodeData.value = null
  closeContextMenu()
  islandExpanded.value = false
}

// ========== 右键菜单 ==========

function onNodeContextMenu({ event, node }: any) {
  event.preventDefault()
  contextMenu.value = {
    visible: true,
    position: { x: event.clientX, y: event.clientY },
    type: 'node',
    nodeId: node.id,
  }
}

function onPaneContextMenu(event: MouseEvent) {
  event.preventDefault()
  contextMenu.value = {
    visible: true,
    position: { x: event.clientX, y: event.clientY },
    type: 'pane',
  }
}

function closeContextMenu() {
  contextMenu.value = { ...contextMenu.value, visible: false }
}

function handleDuplicateNode() {
  const nodeId = contextMenu.value.nodeId
  if (!nodeId) return
  const node = findNode(nodeId)
  if (!node) return

  const data = node.data as FlowNodeData
  const newNode = createNode(
    { type: data.type, displayName: data.label || data.type, category: data.category, isSpecial: data.isSpecial, inputSlots: data.inputSlots, outputSlots: data.outputSlots },
    { x: node.position.x + 40, y: node.position.y + 40 },
  )
  // 只复制参数和适配器，不覆盖 inputSlots/outputSlots（createNode 已正确创建）
  ;(newNode.data as FlowNodeData).params = { ...data.params }
  ;(newNode.data as FlowNodeData).adapterId = data.adapterId
  addNodes([newNode])
  nextTick(() => {
    updateNodeInternals(newNode.id)
  })
  closeContextMenu()
}

/**
 * 复制节点 + 连入该节点的所有上游连线
 * 新节点是 idle 状态（可编辑），原节点的参数和适配器复制过来
 * 清除运行时字段（status/outputUrl 等）
 */
function handleDuplicateWithEdges() {
  const nodeId = contextMenu.value.nodeId
  if (!nodeId) return
  const node = findNode(nodeId)
  if (!node) return

  const data = node.data as FlowNodeData
  const newNode = createNode(
    { type: data.type, displayName: data.label || data.type, category: data.category, isSpecial: data.isSpecial, inputSlots: data.inputSlots, outputSlots: data.outputSlots },
    { x: node.position.x + 60, y: node.position.y + 60 },
  )
  // 复制参数和适配器，清除运行时字段
  const paramsCopy = { ...data.params }
  delete paramsCopy.status
  delete paramsCopy.outputUrl
  delete paramsCopy.outputUrls
  ;(newNode.data as FlowNodeData).params = paramsCopy
  ;(newNode.data as FlowNodeData).adapterId = data.adapterId
  ;(newNode.data as FlowNodeData).status = 'idle'
  addNodes([newNode])

  // 复制连入原节点的所有边（上游连线）
  const incomingEdges = edges.value.filter(e => e.target === nodeId)
  const newEdges = incomingEdges.map(e => ({
    ...e,
    id: `e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    target: newNode.id,
  }))
  if (newEdges.length > 0) {
    addEdges(newEdges)
  }

  nextTick(() => {
    updateNodeInternals(newNode.id)
  })
  closeContextMenu()
}

function handleCopyNodeParams() {
  const nodeId = contextMenu.value.nodeId
  if (!nodeId) return
  const node = findNode(nodeId)
  if (!node) return
  const data = node.data as FlowNodeData
  // 复制参数到剪贴板
  navigator.clipboard.writeText(JSON.stringify({ adapterId: data.adapterId, params: data.params }, null, 2))
    .then(() => console.log('参数已复制'))
    .catch(() => console.log('复制失败'))
  closeContextMenu()
}

function handleDeleteNode() {
  const nodeId = contextMenu.value.nodeId
  if (!nodeId) return
  removeNodes([nodeId])
  nextTick(() => {
    // 确保 VueFlow 完成 DOM 更新，避免异步 patch 已卸载组件
    updateNodeInternals(nodeId)
  })
  // 清除选中状态
  if (selectedNodeId.value === nodeId) {
    selectedNodeId.value = null
    selectedNodeData.value = null
  }
  closeContextMenu()
}

function handleSelectAll() {
  // Vue Flow 没有原生全选，手动选中所有节点
  for (const node of nodes.value) {
    node.selected = true
  }
  closeContextMenu()
}

function clearSelection() {
  selectedNodeId.value = null
  selectedNodeData.value = null
}

// ========== 缩略图右键 → 创建引用节点 ==========

function onCreateRefNode(e: Event) {
  const detail = (e as CustomEvent).detail
  if (!detail) return
  thumbContextMenu.value = {
    visible: true,
    x: detail.x,
    y: detail.y,
    parentNodeId: detail.parentNodeId,
    imageIndex: detail.imageIndex,
  }
}

function confirmCreateRefNode() {
  const { parentNodeId, imageIndex } = thumbContextMenu.value
  const parentNode = findNode(parentNodeId)
  if (!parentNode) {
    thumbContextMenu.value.visible = false
    return
  }

  const parentData = parentNode.data as FlowNodeData
  const parentUrls = parentData.outputUrls || (parentData.outputUrl ? [parentData.outputUrl] : [])
  if (parentUrls.length === 0) {
    thumbContextMenu.value.visible = false
    return
  }

  const screenPos = { x: thumbContextMenu.value.x, y: thumbContextMenu.value.y }
  const flowPos = screenToFlowCoordinate(screenPos)

  const refId = `ref_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
  const parentOutputType = parentData.outputSlots?.[0]?.dataType || 'image'

  const newNode = {
    id: refId,
    type: 'reference' as const,
    position: { x: flowPos.x + 20, y: flowPos.y + 20 },
    data: {
      label: 'reference',
      type: 'reference',
      category: '引用',
      isSpecial: false,
      adapterId: null,
      params: {
        parentNodeId,
        imageIndex,
        parentUrls,
        parentType: parentData.type,
        status: 'completed',
        outputUrl: parentUrls[imageIndex],
      },
      inputSlots: [],
      outputSlots: [{
        name: 'output',
        dataType: parentOutputType,
        displayName: '',
        required: false,
        multi: false,
      }],
      status: 'completed' as const,
      outputUrl: parentUrls[imageIndex],
    } as FlowNodeData,
  }

  addNodes([newNode as any])
  nextTick(() => {
    updateNodeInternals(refId)
  })
  thumbContextMenu.value.visible = false
}

// 参数更新
function onUpdateParams(nodeId: string, params: Record<string, any>) {
  const node = nodes.value.find(n => n.id === nodeId)
  if (node) {
    const data = node.data as FlowNodeData
    // 逐个属性赋值（与 SSE 完成时相同的模式）
    for (const [key, value] of Object.entries(params)) {
      data.params[key] = value
    }
    if (selectedNodeId.value === nodeId) {
      selectedNodeData.value = { ...data, params: { ...data.params } }
    }
  }
}

function onUpdateAdapterId(nodeId: string, adapterId: string | null) {
  const node = findNode(nodeId)
  if (node) {
    const data = node.data as FlowNodeData
    node.data = { ...data, adapterId }
    if (selectedNodeId.value === nodeId) {
      selectedNodeData.value = { ...data, adapterId }
    }
  }
}

// 连线
function onConnect(connection: Connection) {
  const sourceNode = findNode(connection.source)
  const targetNode = findNode(connection.target)
  if (!sourceNode || !targetNode) return

  // 规则 1：不能自连
  if (connection.source === connection.target) return

  // 规则 2：不能重复连线
  const duplicate = edges.value.find(e =>
    e.source === connection.source && e.target === connection.target
  )
  if (duplicate) return

  const sourceData = sourceNode.data as FlowNodeData
  const targetData = targetNode.data as FlowNodeData

  // 从 handle ID 找端口
  const sourceSlot = sourceData.outputSlots?.find(s => s.name === connection.sourceHandle)
  const targetSlot = targetData.inputSlots?.find(s => s.name === connection.targetHandle)

  // 规则 3：类型校验
  if (sourceSlot && targetSlot) {
    if (!canConnectPorts(sourceSlot.dataType as PortTypeCode, targetSlot.dataType as PortTypeCode)) {
      console.warn(`不兼容的连线: ${sourceSlot.dataType} → ${targetSlot.dataType}`)
      return
    }
  }

  const dataType = sourceSlot?.dataType || targetSlot?.dataType || 'video'

  const edge = createEdge(
    connection.source,
    connection.sourceHandle,
    connection.target,
    connection.targetHandle,
    dataType,
  )
  addEdges([edge])
}

// 分类节点类型
const nodeTypeList = ref<NodeTypeSchema[]>([])

const categorizedSchemas = computed(() => {
  const result: Record<string, NodeTypeSchema[]> = {}
  const query = searchQuery.value.toLowerCase()

  for (const schema of nodeTypeList.value) {
    if (query && !schema.displayName.toLowerCase().includes(query)) continue
    const cat = schema.category || '其他'
    if (!result[cat]) result[cat] = []
    result[cat].push(schema)
  }
  return result
})

// ========== 右键“成为主体”（方案C） ==========

/** 右键“成为主体” → 弹出创建对话框 */
async function handleBecomeElement() {
  const nodeId = contextMenu.value.nodeId
  if (!nodeId) return
  const node = findNode(nodeId)
  if (!node) { closeContextMenu(); return }

  const data = node.data as FlowNodeData
  // 获取节点的图片输出 URL
  const outputUrls = data.outputUrls || (data.outputUrl ? [data.outputUrl] : [])
  const rawUrl = outputUrls[0] || ''

  // 从 URL 中提取 mediaId
  const mediaIdMatch = rawUrl.match(/\/media\/(\d+)\/file/)
  const mediaId = mediaIdMatch ? parseInt(mediaIdMatch[1]) : null

  // 用 useMediaLoader 获取 blob URL（带 Authorization header）
  let thumbUrl = rawUrl
  if (mediaId) {
    try {
      thumbUrl = await mediaLoader.loadMedia({ id: mediaId, url: rawUrl })
    } catch {
      // fallback 用原始 URL
    }
  }

  elementDialog.value = {
    visible: true,
    sourceNodeId: nodeId,
    sourceNodeName: data.label || data.type,
    sourceThumb: thumbUrl,
    elementName: '',
    elementDesc: '',
    tagId: 'o_102',
    creating: false,
    statusMsg: '',
    statusType: '',
  }
  closeContextMenu()
}

/** 确认创建主体 → 调 API → 创建 ElementSource 节点 */
async function confirmBecomeElement() {
  if (!elementDialog.value.elementName.trim()) {
    elementDialog.value.statusMsg = '请输入主体名称'
    elementDialog.value.statusType = 'error'
    return
  }

  const node = findNode(elementDialog.value.sourceNodeId)
  if (!node) return

  const data = node.data as FlowNodeData
  // 从节点参数或输出中提取 mediaId
  // 对于 Capability 节点：outputUrls 里存的是 media proxy URL，需要解析 mediaId
  // 对于输入节点：params.url 可能是外部 URL
  const mediaIds: number[] = []
  const outputUrls = data.outputUrls || (data.outputUrl ? [data.outputUrl] : [])
  for (const url of outputUrls) {
    if (!url) continue
    // 从 /api/v1/media/{id}/file 提取 mediaId
    const match = url.match(/\/media\/(\d+)\/file/)
    if (match) {
      mediaIds.push(parseInt(match[1]))
    }
  }

  if (mediaIds.length === 0) {
    elementDialog.value.statusMsg = '无法获取图片 mediaId，请确保节点已执行且有图片输出'
    elementDialog.value.statusType = 'error'
    return
  }

  elementDialog.value.creating = true
  elementDialog.value.statusMsg = '正在生成参考图并创建主体...'
  elementDialog.value.statusType = 'pending'

  try {
    const res = await api.autoCreateKlingElement({
      element_name: elementDialog.value.elementName,
      element_desc: elementDialog.value.elementDesc,
      media_id: mediaIds[0],  // 只传第一张作为正面图
      tag_id: elementDialog.value.tagId,
    })

    // auto-from-media 异步模式：返回 job_id
    const jobId = res.job_id
    if (jobId) {
      elementDialog.value.statusMsg = '正在生成参考图...'
      pollAutoElementJobInCanvas(jobId, node.position.x + 60, node.position.y)
      return
    }

    // 兼容旧格式（同步返回）
    const klingResult = res.kling_result || res
    const taskId = klingResult.task_id || klingResult.data?.task_id
    const refImageUrls = res.ref_image_urls || []
    if (taskId) {
      elementDialog.value.statusMsg = '主体创建中，等待 Kling 处理...'
      pollElementCreation(taskId, node.position.x + 60, node.position.y, refImageUrls)
    } else {
      const elementId = klingResult.element_id || klingResult.data?.element_id
      finishElementCreation(elementId, node.position.x + 60, node.position.y, refImageUrls)
    }
  } catch (e: any) {
    elementDialog.value.statusMsg = '创建失败: ' + (e.message || '未知错误')
    elementDialog.value.statusType = 'error'
    elementDialog.value.creating = false
  }
}

/** 轮询 auto-from-media 异步任务（画布弹窗版） */
async function pollAutoElementJobInCanvas(jobId: string, posX: number, posY: number) {
  const maxAttempts = 80
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(r => setTimeout(r, 3000))
    try {
      const job = await api.getAutoElementJobStatus(jobId)
      const status = job.status
      const step = job.step || ''

      if (status === 'failed') {
        elementDialog.value.statusMsg = '创建失败: ' + (job.error || '未知错误')
        elementDialog.value.statusType = 'error'
        elementDialog.value.creating = false
        return
      }

      if (status === 'kling_processing') {
        const klingTaskId = job.kling_task_id
        if (klingTaskId) {
          elementDialog.value.statusMsg = '主体创建中，等待 Kling 处理...'
          pollElementCreation(klingTaskId, posX, posY, job.ref_image_urls || [])
          return
        }
      }

      if (step) {
        elementDialog.value.statusMsg = step
      }
    } catch (e) {
      console.error('[BecomeElement] 异步任务轮询失败:', e)
    }
  }
  elementDialog.value.statusMsg = '轮询超时'
  elementDialog.value.statusType = 'error'
  elementDialog.value.creating = false
}

/** 轮询主体创建任务 */
async function pollElementCreation(taskId: string, posX: number, posY: number, refImageUrls: string[] = []) {
  const maxAttempts = 80
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(r => setTimeout(r, 3000))
    try {
      const res = await api.pollKlingElementTask(taskId)
      // Kling API 返回格式：{ code: 0, data: { task_status: "succeed", task_result: { elements: [...] } } }
      const data = res.data || res
      const taskStatus = data.task_status || res.status || res.task_status
      const runState = data.run_state ?? res.run_state
      if (taskStatus === 'succeed' || taskStatus === 'succeeded' || runState === 3) {
        const elements = data.task_result?.elements || []
        const element = elements[0] || {}
        const elementId = element.element_id || data.element_id || res.element_id
        // 提取 Kling 返回的正面图 URL + 标签
        const faceUrl = element.element_image_list?.frontal_image || element.face_image_url
        const tagList = element.tag_list || []
        // 参考图：优先 Kling 返回的，其次后端翻转生成的
        const klingRefImages = element.element_image_list?.refer_images || []
        const finalRefUrls = klingRefImages.length
          ? klingRefImages.map((r: any) => r.image_url || r.url || r)
          : refImageUrls
        finishElementCreation(elementId, posX, posY, finalRefUrls, faceUrl, tagList)
        return
      }
      if (taskStatus === 'failed' || runState === 4) {
        elementDialog.value.statusMsg = '主体创建失败'
        elementDialog.value.statusType = 'error'
        elementDialog.value.creating = false
        return
      }
    } catch (e) {
      console.error('[BecomeElement] 轮询失败:', e)
    }
  }
  elementDialog.value.statusMsg = '轮询超时'
  elementDialog.value.statusType = 'error'
  elementDialog.value.creating = false
}

/** 主体创建完成 → 关闭弹窗 + 创建 ElementSource 节点 */
function finishElementCreation(elementId: any, posX: number, posY: number, refImageUrls: string[] = [], faceUrl?: string, tagList?: any[]) {
  elementDialog.value.statusMsg = '创建成功!'
  elementDialog.value.statusType = 'success'
  elementDialog.value.creating = false

  // 在画布上创建 ElementSource 节点
  const newNode = createNode(
    { type: 'ElementSource', displayName: '主体', category: '输入', isSpecial: true, inputSlots: [], outputSlots: [{ name: 'element', dataType: 'element', displayName: '主体', required: false, multi: false }] },
    { x: posX, y: posY }
  )
  ;(newNode.data as FlowNodeData).params = {
    elementId: elementId,
    elementName: elementDialog.value.elementName,
    elementDesc: elementDialog.value.elementDesc || '',
    elementThumb: faceUrl || elementDialog.value.sourceThumb,
    sourceNodeName: elementDialog.value.sourceNodeName,
    refImageUrls: refImageUrls,
    tagList: tagList || [],
  }
  addNodes([newNode])
  nextTick(() => {
    updateNodeInternals(newNode.id)
  })

  // 自动选中新节点
  selectedNodeId.value = newNode.id
  selectedNodeData.value = JSON.parse(JSON.stringify(newNode.data)) as FlowNodeData

  // 延迟关闭弹窗
  setTimeout(() => { elementDialog.value.visible = false }, 1000)
}

// 添加节点
// 打开添加节点面板（从右键菜单）
function openAddPanelFromContext() {
  // 将右键屏幕坐标转为画布坐标
  addPanelFlowPos.value = screenToFlowCoordinate({ x: contextMenu.value.position.x, y: contextMenu.value.position.y })
  islandExpanded.value = false
  showAddPanel.value = true
  closeContextMenu()
}

// 打开添加节点面板（从工具栏按钮）
function openAddPanelCenter() {
  addPanelFlowPos.value = { x: -1, y: -1 }
  islandExpanded.value = true
}

// 添加节点
function addNodeFromSchema(schema: any) {
  let position: { x: number; y: number }
  if (addPanelFlowPos.value.x >= 0 && addPanelFlowPos.value.y >= 0) {
    // 右键触发：新节点放在右键点击的画布位置，稍微偏移
    position = {
      x: addPanelFlowPos.value.x - 40,
      y: addPanelFlowPos.value.y - 20,
    }
  } else {
    // 工具栏触发：随机位置
    position = {
      x: 200 + Math.random() * 200,
      y: 200 + Math.random() * 200,
    }
  }
  const node = createNode(schema, position)
  addNodes([node])
  // 延迟更新节点内部状态，确保 DOM 已渲染
  nextTick(() => {
    updateNodeInternals(node.id)
  })
  showAddPanel.value = false
}

// 从后端加载工作流
function loadFromWorkflow(wf: any) {
  const { nodes: loadedNodes, edges: loadedEdges } = deserializeWorkflow(wf)
  // 诊断日志已清理

  // 先清空，再用 setNodes/setEdges 分步加载，确保 VueFlow 图引用正确
  setEdges([])
  setNodes([])
  nextTick(() => {
    setNodes(loadedNodes)
    nextTick(() => {
      setEdges(loadedEdges)
      nextTick(() => {
        for (const node of loadedNodes) {
          updateNodeInternals(node.id)
        }
      })
    })
  })
}

function handleFitView() {
  fitView()
}

// ========== 灵动岛占位入口 ==========

function handleAssetLibrary() {
  alert('素材库功能即将上线 🚧')
}

function handleTemplates() {
  alert('模板功能即将上线 🚧')
}

// ========== 保存逻辑 ==========

/** 首次保存：弹窗让用户填名称和备注 */
function handleSave() {
  // 名称是默认值时弹窗让用户命名
  if (workflowName.value === '未命名工作流' || !currentWorkflowId.value) {
    saveDialogName.value = workflowName.value === '未命名工作流' ? '' : workflowName.value
    saveDialogDesc.value = workflowDesc.value
    showSaveDialog.value = true
    nextTick(() => {
      saveNameInput.value?.focus()
    })
  } else {
    doSave()
  }
}

/** 弹窗确认保存 */
async function confirmSaveDialog() {
  if (!saveDialogName.value.trim()) return
  workflowName.value = saveDialogName.value.trim()
  workflowDesc.value = saveDialogDesc.value.trim()
  showSaveDialog.value = false
  await doSave()
}

function cancelSaveDialog() {
  showSaveDialog.value = false
}

/** 点击标题编辑名称+备注 */
function startEditName() {
  nameEditValue.value = workflowName.value
  nameEditDesc.value = workflowDesc.value
  showNameEdit.value = true
  nextTick(() => {
    nameEditInput.value?.focus()
    nameEditInput.value?.select()
  })
}

async function confirmNameEdit() {
  const newName = nameEditValue.value.trim()
  const newDesc = nameEditDesc.value.trim()
  if (!newName) return
  workflowName.value = newName
  workflowDesc.value = newDesc
  showNameEdit.value = false
  if (currentWorkflowId.value) {
    await doSave()
  }
}

function cancelNameEdit() {
  showNameEdit.value = false
}

/** 实际保存请求 */
async function doSave() {
  isSaving.value = true
  const { nodes: wfNodes, edges: wfEdges } = serializeToWorkflow(nodes.value, edges.value)

  const payload = {
    name: workflowName.value,
    description: workflowDesc.value || null,
    nodes: JSON.stringify(wfNodes),
    edges: JSON.stringify(wfEdges),
    variables: '{}',
    projectId: props.projectId,
    isTemplate: false,
    status: 'draft',
  }

  try {
    if (currentWorkflowId.value) {
      await flowApi.updateWorkflow(currentWorkflowId.value, payload)
    } else {
      const wf = await flowApi.createWorkflow(payload as any)
      currentWorkflowId.value = wf.id
      emit('saved', wf.id)
    }
  } catch (e: any) {
    console.error('保存失败:', e)
  } finally {
    isSaving.value = false
    hasUnsavedChanges.value = false
  }
}

// ========== 执行 ==========

async function handleExecute() {
  if (isExecuting.value) return

  // 执行前必须先保存最新数据
  await doSave()
  if (!currentWorkflowId.value) return

  isExecuting.value = true
  completedCount.value = 0
  totalCount.value = nodes.value.length

  // 重置所有节点状态
  for (const node of nodes.value) {
    const data = node.data as FlowNodeData
    data.status = 'idle'
    data.outputUrl = undefined
    delete data.params.outputUrl
    delete data.outputUrls
    delete data.params.outputUrls
    delete data.params.status
  }

  try {
    const { user } = useAuth()
    const userId = user.value?.id
    if (!userId) {
      console.error('未登录，无法执行工作流')
      isExecuting.value = false
      return
    }
    const result = await flowApi.executeWorkflow(currentWorkflowId.value!, userId)
    const executionId = result.executionId

    // 建立 SSE 连接
    eventSource = flowApi.streamExecution(executionId, {
      onExecutionStarted: (data: any) => {
        console.log('工作流开始执行:', data)
      },
      onNodeStarted: (data: any) => {
        const node = nodes.value.find(n => n.id === data.nodeId)
        if (node) {
          ;(node.data as FlowNodeData).status = 'running'
        }
      },
      onNodeCompleted: (data: any) => {
        const node = nodes.value.find(n => n.id === data.nodeId)
        if (node) {
          const nodeData = node.data as FlowNodeData
          nodeData.status = 'completed'
          // 写入 params 以便保存时持久化
          nodeData.params.status = 'completed'
          if (data.outputUrl) {
            nodeData.outputUrl = data.outputUrl
            nodeData.params.outputUrl = data.outputUrl
          }
          // 多结果
          if (data.outputUrls && data.outputUrls.length > 1) {
            nodeData.outputUrls = data.outputUrls
            nodeData.params.outputUrls = data.outputUrls
          } else {
            // 单结果，确保 outputUrls 不存在
            delete nodeData.outputUrls
            delete nodeData.params.outputUrls
          }
          completedCount.value++
        }
      },
      onNodeFailed: (data: any) => {
        const node = nodes.value.find(n => n.id === data.nodeId)
        if (node) {
          ;(node.data as FlowNodeData).status = 'failed'
          ;(node.data as FlowNodeData).params.status = 'failed'
          ;(node.data as FlowNodeData).errorMsg = data.error || '执行失败'
          completedCount.value++
        }
      },
      onNodeProgress: (data: any) => {
        // 轮询进度，可扩展
      },
      onExecutionCompleted: () => {
        isExecuting.value = false
        if (eventSource) { eventSource.close(); eventSource = null }
      },
      onExecutionFailed: (data: any) => {
        isExecuting.value = false
        const msg = data?.error || '工作流执行失败'
        console.error('工作流执行失败:', msg)
        alert(msg)
        console.error('工作流执行失败:', data.error)
        if (eventSource) { eventSource.close(); eventSource = null }
      },
    })
  } catch (e) {
    console.error('执行失败:', e)
    isExecuting.value = false
  }
}

function handleStop() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  isExecuting.value = false
}

// ========== 单节点执行 ==========

async function handleExecuteNode() {
  const nodeId = contextMenu.value.nodeId
  if (!nodeId) {
    console.warn('无法执行：缺少 nodeId')
    closeContextMenu()
    return
  }
  closeContextMenu()

  // 防御性检查：已有输出且有下游连线时不允许重复执行
  const checkNode = findNode(nodeId)
  if (checkNode) {
    const checkData = checkNode.data as FlowNodeData
    const hasOutput = !!(checkData.outputUrl || (checkData.outputUrls && checkData.outputUrls.length > 0))
    const hasDownstream = edges.value.some(e => e.source === nodeId)
    if (hasOutput && hasDownstream) {
      console.warn('节点已有输出且存在下游连线，不允许重复执行')
      return
    }
  }

  // 执行前先保存（首次保存会创建 workflowId）
  await doSave()
  if (!currentWorkflowId.value) {
    console.warn('保存失败，无法执行')
    return
  }

  const node = findNode(nodeId)
  if (!node) return

  isExecuting.value = true
  completedCount.value = 0
  totalCount.value = 1

  // 重置当前节点状态
  const data = node.data as FlowNodeData
  data.status = 'running'
  data.outputUrl = undefined
  delete data.params.outputUrl
  delete data.outputUrls
  delete data.params.outputUrls
  delete data.params.status

  try {
    const { user } = useAuth()
    const userId = user.value?.id
    if (!userId) {
      console.error('未登录，无法执行节点')
      isExecuting.value = false
      return
    }

    const result = await flowApi.executeNode(currentWorkflowId.value!, userId, nodeId)
    const executionId = result.executionId

    // 复用 SSE 监听
    eventSource = flowApi.streamExecution(executionId, {
      onNodeStarted: (d: any) => {
        // 已标记 running
      },
      onNodeCompleted: (d: any) => {
        data.status = 'completed'
        data.params.status = 'completed'
        if (d.outputUrl) {
          data.outputUrl = d.outputUrl
          data.params.outputUrl = d.outputUrl
        }
        // 多结果
        if (d.outputUrls && d.outputUrls.length > 1) {
          data.outputUrls = d.outputUrls
          data.params.outputUrls = d.outputUrls
        } else {
          delete data.outputUrls
          delete data.params.outputUrls
        }
        completedCount.value++
      },
      onNodeFailed: (d: any) => {
        data.status = 'failed'
        data.params.status = 'failed'
        data.errorMsg = d.error || '执行失败'
        completedCount.value++
      },
      onExecutionCompleted: () => {
        isExecuting.value = false
        if (eventSource) { eventSource.close(); eventSource = null }
      },
      onExecutionFailed: (d: any) => {
        isExecuting.value = false
        const msg = d?.error || '执行失败'
        console.error('单节点执行失败:', msg)
        alert(msg)
        if (eventSource) { eventSource.close(); eventSource = null }
      },
    })
  } catch (e) {
    console.error('单节点执行失败:', e)
    isExecuting.value = false
  }
}

// 全局点击关闭右键菜单
function onGlobalClick() {
  if (contextMenu.value.visible) {
    closeContextMenu()
  }
  if (thumbContextMenu.value.visible) {
    thumbContextMenu.value.visible = false
  }
}

// ========== 初始化 ==========

onMounted(async () => {
  document.addEventListener('click', onGlobalClick)
  canvasWrapperRef.value?.addEventListener('create-ref-node', onCreateRefNode as EventListener)

  // 加载节点类型
  try {
    const types = await flowApi.getNodeTypes()
    nodeTypeList.value = Object.values(types)
  } catch (e) {
    console.error('加载节点类型失败:', e)
  }

  // 加载已有工作流
  if (props.workflowId) {
    try {
      const wf = await flowApi.getWorkflow(props.workflowId)
      console.log('[FlowCanvas] getWorkflow response:', wf)
      console.log('[FlowCanvas] wf.id:', wf?.id, 'wf.nodes type:', typeof wf?.nodes, 'wf.edges type:', typeof wf?.edges)
      console.log('[FlowCanvas] wf.nodes sample:', typeof wf?.nodes === 'string' ? wf.nodes.substring(0, 100) : JSON.stringify(wf?.nodes)?.substring(0, 100))
      workflowName.value = wf.name || '未命名工作流'
      workflowDesc.value = wf.description || ''
      currentWorkflowId.value = wf.id
      loadFromWorkflow(wf)
    } catch (e) {
      console.error('加载工作流失败:', e)
    }
  }
})

onBeforeUnmount(() => {
  // 先清空数据，避免 VueFlow 在组件卸载后继续 patch
  setNodes([])
  setEdges([])
})

onUnmounted(() => {
  document.removeEventListener('click', onGlobalClick)
  canvasWrapperRef.value?.removeEventListener('create-ref-node', onCreateRefNode as EventListener)
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})

function miniMapNodeColor(node: any) {
  const data = node.data as FlowNodeData
  const typeColors: Record<string, string> = {
    '视频': '#D94A4A',
    '图片': '#4A90D9',
    '音频': '#4AD97A',
    '输入': '#95A5A6',
    '输出': '#95A5A6',
    '控制': '#9B59B6',
  }
  return typeColors[data.category] || '#475569'
}
</script>

<style scoped>
/* ========== Flow 主题变量 ========== */
.flow-dark {
  --flow-bg: #000000;
  --flow-bg-deep: #05080f;
  --flow-surface: #0f1724;
  --flow-surface-hover: #1a2332;
  --flow-border: #1e293b;
  --flow-border-hover: #334155;
  --flow-text: #e2e8f0;
  --flow-text-muted: #94a3b8;
  --flow-text-dim: #64748b;
  --flow-icon: #2d3a4f;
  --flow-icon-hover: #8899aa;
  --flow-minimap-bg: #0a0f1a;
  --flow-grid: #1e293b;
  --flow-node-bg: linear-gradient(145deg, #0f1724 0%, #05080f 100%);
  --flow-accent: #3b82f6;
  --flow-hover-bg: #1a2332;
  --flow-ctrl-hover-bg: rgba(255, 255, 255, 0.08);
}

.flow-light {
  --flow-bg: #f8fafc;
  --flow-bg-deep: #f1f5f9;
  --flow-surface: #ffffff;
  --flow-surface-hover: #f1f5f9;
  --flow-border: #e2e8f0;
  --flow-border-hover: #cbd5e1;
  --flow-text: #1e293b;
  --flow-text-muted: #64748b;
  --flow-text-dim: #94a3b8;
  --flow-icon: #94a3b8;
  --flow-icon-hover: #64748b;
  --flow-minimap-bg: #f1f5f9;
  --flow-grid: #e2e8f0;
  --flow-node-bg: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
  --flow-accent: #3b82f6;
  --flow-hover-bg: #e2e8f0;
  --flow-ctrl-hover-bg: rgba(0, 0, 0, 0.05);
}

.flow-canvas-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--flow-bg);
  color: var(--flow-text);
}

/* ========== 灵动岛 ========== */
.dynamic-island {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  flex-direction: column;
  border-radius: 28px;
  background: rgba(20, 20, 20, 0.35);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
  transition: background 0.3s, border-color 0.3s;
  max-width: 92vw;
}

.flow-light .dynamic-island {
  background: rgba(255, 255, 255, 0.4);
  border-color: rgba(0, 0, 0, 0.08);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

/* 顶栏：始终显示 */
.island-bar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
}

.island-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 7px 12px;
  font-size: 13px;
  border: none;
  border-radius: 20px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
  white-space: nowrap;
}

.flow-light .island-btn {
  color: rgba(0, 0, 0, 0.65);
}

.island-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.95);
}

.flow-light .island-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: rgba(0, 0, 0, 0.9);
}

.island-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.island-name {
  font-size: 13px;
  font-weight: 600;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.island-divider {
  width: 1px;
  height: 18px;
  background: rgba(255, 255, 255, 0.12);
  flex-shrink: 0;
}

.flow-light .island-divider {
  background: rgba(0, 0, 0, 0.1);
}

/* 保存按钮小红点 */
.island-dot {
  position: absolute;
  top: 4px;
  right: 6px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
}

/* 保存中 spinner */
.island-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-top-color: rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  animation: island-spin 0.6s linear infinite;
}

.flow-light .island-spinner {
  border-color: rgba(0, 0, 0, 0.15);
  border-top-color: rgba(0, 0, 0, 0.7);
}

@keyframes island-spin {
  to { transform: rotate(360deg); }
}

/* 图标统一样式 */
.island-icon {
  flex-shrink: 0;
  display: inline-block;
}

/* Tooltip 冒泡提示 */
.island-tooltip {
  position: absolute;
  bottom: -30px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  border-radius: 6px;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.18s ease 0s;
  background: rgba(0, 0, 0, 0.82);
  color: rgba(255, 255, 255, 0.9);
  z-index: 30;
}

.flow-light .island-tooltip {
  background: rgba(0, 0, 0, 0.75);
  color: rgba(255, 255, 255, 0.92);
}

.island-btn:hover .island-tooltip {
  opacity: 1;
  transition-delay: 0.3s;
}

/* 灵动岛按钮激活态 */
.island-btn-active {
  background: rgba(255, 255, 255, 0.12) !important;
  color: rgba(255, 255, 255, 0.95) !important;
}

.flow-light .island-btn-active {
  background: rgba(0, 0, 0, 0.06) !important;
  color: rgba(0, 0, 0, 0.9) !important;
}

/* ========== 灵动岛展开区（岛内延伸） ========== */
.island-body {
  width: 420px;
  max-width: 92vw;
  padding: 0 8px 8px;
}

.island-separator {
  height: 1px;
  background: rgba(255, 255, 255, 0.08);
  margin: 0 4px 8px;
}

.flow-light .island-separator {
  background: rgba(0, 0, 0, 0.06);
}

/* 分类标签栏 */
.island-tabs {
  display: flex;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: none;
  margin-bottom: 8px;
}
.island-tabs::-webkit-scrollbar { display: none; }

.island-tab {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 500;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: all 0.18s;
  white-space: nowrap;
}

.flow-light .island-tab {
  color: rgba(0, 0, 0, 0.45);
}

.island-tab:hover {
  color: rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.06);
}

.flow-light .island-tab:hover {
  background: rgba(0, 0, 0, 0.04);
  color: rgba(0, 0, 0, 0.7);
}

.island-tab-active {
  background: rgba(255, 255, 255, 0.1) !important;
  color: rgba(255, 255, 255, 0.95) !important;
}

.flow-light .island-tab-active {
  background: rgba(0, 0, 0, 0.06) !important;
  color: rgba(0, 0, 0, 0.9) !important;
}

/* 节点列表 */
.island-node-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
  scrollbar-width: thin;
  padding: 2px 4px 4px;
}

.island-node-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  font-size: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.18s;
}

.flow-light .island-node-item {
  background: rgba(0, 0, 0, 0.03);
  border-color: rgba(0, 0, 0, 0.08);
  color: rgba(0, 0, 0, 0.65);
}

.island-node-item:hover {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.4);
  color: rgba(255, 255, 255, 0.95);
}

.flow-light .island-node-item:hover {
  background: rgba(59, 130, 246, 0.08);
  color: rgba(0, 0, 0, 0.9);
}

.island-node-icon {
  font-size: 13px;
  line-height: 1;
}

.island-node-label {
  font-weight: 500;
}

/* 岛内展开动画 */
.island-grow-enter-active,
.island-grow-leave-active {
  transition: opacity 0.22s ease, max-height 0.22s ease;
  overflow: hidden;
}
.island-grow-enter-from,
.island-grow-leave-to {
  opacity: 0;
  max-height: 0;
}
.island-grow-enter-to,
.island-grow-leave-from {
  opacity: 1;
  max-height: 280px;
}

/* ========== 画布容器 ========== */
.canvas-container {
  flex: 1;
  overflow: hidden;
  position: relative;
}

/* ========== Vue Flow 全局覆写 ========== */
:deep(.vue-flow) {
  background: var(--flow-bg);
}

:deep(.vue-flow__edge-path) {
  stroke: var(--flow-accent);
  stroke-width: 2;
}

:deep(.vue-flow__edge.animated .vue-flow__edge-path) {
  stroke-dasharray: 5;
  animation: dash 0.6s linear infinite;
}

@keyframes dash {
  to { stroke-dashoffset: -10; }
}

:deep(.vue-flow__connection-line) {
  stroke: var(--flow-accent);
  stroke-width: 2;
  stroke-dasharray: 5;
}

:deep(.vue-flow__minimap) {
  background: var(--flow-minimap-bg);
  border: 1px solid var(--flow-border);
  border-radius: 8px;
  bottom: 52px !important;
}

:deep(.vue-flow__controls) {
  background: var(--flow-surface);
  border: 1px solid var(--flow-border);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

/* VueFlow 控制按钮覆写 */
:deep(.vue-flow__controls-button) {
  background: var(--flow-surface);
  border-bottom: 1px solid var(--flow-border);
  color: var(--flow-text-muted);
  fill: var(--flow-text-muted);
}

:deep(.vue-flow__controls-button:hover) {
  background: var(--flow-hover-bg);
  color: var(--flow-text);
  fill: var(--flow-text);
}

/* Handle 点样式 */
:deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
  border: 2px solid var(--flow-border);
  transition: all 0.2s;
}

:deep(.vue-flow__handle:hover) {
  transform: scale(1.3);
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.5);
}

/* ========== 执行状态提示 ========== */
.execution-status {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--flow-surface);
  border: 1px solid #f59e0b;
  border-radius: 12px;
  padding: 10px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #fbbf24;
  z-index: 50;
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.25);
}

.status-pulse {
  width: 8px;
  height: 8px;
  background: #f59e0b;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* ========== 缩略图右键菜单 ========== */
.thumb-context-menu {
  position: fixed;
  z-index: 100;
  background: var(--flow-surface);
  border: 1px solid var(--flow-border-hover);
  border-radius: 8px;
  padding: 4px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.thumb-menu-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--flow-text);
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}

.thumb-menu-btn:hover {
  background: var(--flow-hover-bg);
  color: #a855f7;
}

/* ========== 添加节点面板 ========== */
.add-panel-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-panel {
  background: var(--flow-surface);
  border: 1px solid var(--flow-border);
  border-radius: 16px;
  width: 380px;
  max-height: 70vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}

.add-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--flow-border);
}

.add-panel-header h3 {
  margin: 0;
  font-size: 16px;
  background: linear-gradient(135deg, var(--flow-text) 0%, var(--flow-text-muted) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.close-btn {
  background: none;
  border: none;
  color: var(--flow-text-dim);
  font-size: 18px;
  cursor: pointer;
  transition: color 0.2s;
}

.close-btn:hover {
  color: var(--flow-text);
}

.search-input {
  margin: 12px 16px;
  padding: 8px 12px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 8px;
  color: var(--flow-text);
  font-size: 13px;
  outline: none;
}

.search-input:focus {
  border-color: var(--flow-accent);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

.add-panel-categories {
  flex: 1;
  overflow-y: auto;
  padding: 0 16px 16px;
}

.category-group {
  margin-bottom: 16px;
}

/* 分类标题 */
.category-title {
  font-size: 11px;
  color: var(--flow-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0 0 8px;
}

.category-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.add-item-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  background: var(--flow-surface);
  border: 1px solid var(--flow-border-hover);
  border-radius: 8px;
  color: var(--flow-text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-item-btn:hover {
  background: var(--flow-hover-bg);
  border-color: var(--flow-accent);
  color: var(--flow-text);
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.15);
}

.add-item-icon {
  font-size: 14px;
}

/* ========== 画布控制面板（左下角，小地图下方） ========== */
.canvas-controls {
  position: absolute;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  background: linear-gradient(180deg, var(--flow-surface-hover) 0%, var(--flow-surface) 100%);
  border: 1px solid var(--flow-border-hover);
  border-radius: 8px;
  padding: 4px 6px;
  z-index: 10;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.ctrl-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: none;
  border-radius: 4px;
  color: var(--flow-text-dim);
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.ctrl-btn:hover {
  color: var(--flow-text);
  background: var(--flow-ctrl-hover-bg);
}

.ctrl-btn.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.12);
}

.ctrl-btn svg {
  width: 16px;
  height: 16px;
}

.zoom-slider-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}

.zoom-slider {
  -webkit-appearance: none;
  appearance: none;
  width: 80px;
  height: 4px;
  background: var(--flow-border-hover);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}

.zoom-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 12px;
  height: 12px;
  background: #3b82f6;
  border-radius: 50%;
  border: none;
  cursor: pointer;
}

.zoom-slider::-moz-range-thumb {
  width: 12px;
  height: 12px;
  background: #3b82f6;
  border-radius: 50%;
  border: none;
  cursor: pointer;
}

.zoom-label {
  font-size: 10px;
  color: var(--flow-text-muted);
  min-width: 30px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* 灵动岛名称编辑图标已在 .island-name 中处理 */

/* 首次保存弹窗 */
.save-dialog-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.save-dialog {
  background: var(--flow-surface);
  border: 1px solid var(--flow-border);
  border-radius: 12px;
  padding: 24px;
  width: 380px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}
.save-dialog h3 {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--flow-text);
}
.save-dialog-field {
  margin-bottom: 14px;
}
.save-dialog-field label {
  display: block;
  font-size: 12px;
  color: var(--flow-text-muted);
  margin-bottom: 4px;
}
.save-dialog-field input,
.save-dialog-field textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--flow-border);
  border-radius: 6px;
  background: var(--flow-bg);
  color: var(--flow-text);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.save-dialog-field input:focus,
.save-dialog-field textarea:focus {
  border-color: var(--flow-accent);
}
.save-dialog-field textarea {
  resize: vertical;
  font-family: inherit;
}
.save-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
.save-dialog-btn {
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid var(--flow-border);
  transition: background 0.2s;
}
.save-dialog-btn.cancel {
  background: transparent;
  color: var(--flow-text-muted);
}
.save-dialog-btn.cancel:hover {
  background: var(--flow-hover-bg);
}
.save-dialog-btn.confirm {
  background: var(--flow-accent);
  color: #fff;
  border-color: var(--flow-accent);
}
.save-dialog-btn.confirm:hover {
  opacity: 0.9;
}

/* ==================== 成为主体弹窗 ==================== */
.element-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.element-dialog {
  background: var(--flow-surface);
  border: 1px solid var(--flow-border-hover);
  border-radius: 12px;
  padding: 20px;
  min-width: 320px;
  max-width: 400px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.6);
}

.element-dialog h3 {
  margin: 0 0 12px;
  font-size: 16px;
  color: var(--flow-text);
}

.dialog-source-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 8px;
  background: var(--flow-bg);
  border-radius: 8px;
}

.dialog-source-thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  object-fit: cover;
}

.dialog-source-info span {
  font-size: 12px;
  color: var(--flow-text-muted);
}

.dialog-field {
  margin-bottom: 12px;
}

.dialog-field label {
  display: block;
  font-size: 12px;
  color: var(--flow-text-muted);
  margin-bottom: 4px;
}

.required-star {
  color: #f87171;
}

.dialog-input {
  width: 100%;
  padding: 8px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text);
  font-size: 13px;
}

.dialog-input:focus {
  outline: none;
  border-color: var(--flow-accent);
}

.dialog-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.dialog-cancel {
  flex: 1;
  padding: 8px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text-muted);
  cursor: pointer;
  font-size: 13px;
}

.dialog-confirm {
  flex: 1;
  padding: 8px;
  background: #ff6b6b;
  border: none;
  border-radius: 6px;
  color: white;
  cursor: pointer;
  font-size: 13px;
}

.dialog-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.dialog-status {
  margin-top: 10px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  text-align: center;
}

.dialog-status.pending {
  background: rgba(217, 160, 74, 0.15);
  color: #D9A04A;
}

.dialog-status.success {
  background: rgba(74, 217, 122, 0.15);
  color: #4AD97A;
}

.dialog-status.error {
  background: rgba(217, 74, 74, 0.15);
  color: #D94A4A;
}

.dialog-hint {
  font-size: 12px;
  color: var(--flow-text-secondary);
  padding: 8px 12px;
  background: rgba(255, 107, 107, 0.08);
  border-radius: 6px;
  margin: 4px 0 8px;
}

</style>