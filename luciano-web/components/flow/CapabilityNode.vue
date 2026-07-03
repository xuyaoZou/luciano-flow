<!-- Capability 节点 — 纯图标 + 固定端口圆点，暗色科技风 -->
<template>
  <div
    class="capability-node"
    :class="[
      `status-${data.status || 'idle'}`,
      selected ? 'selected' : '',
      hasMultiResults ? 'multi-result' : '',
    ]"
    :style="{ '--accent': categoryAccent }"
  >
    <!-- 图标区（完成后隐藏，多结果时也隐藏） -->
    <div v-if="!(resolvedOutputUrl && data.status === 'completed')" class="node-icon-area">
      <svg class="node-svg" viewBox="0 0 24 24" fill="currentColor">
        <path :d="nodeIcon" />
      </svg>
      <!-- 状态指示 -->
      <span v-if="data.status === 'running'" class="status-dot running"></span>
      <span v-else-if="data.status === 'failed'" class="status-dot failed"></span>
    </div>
    <!-- 错误提示 -->
    <div v-if="data.status === 'failed' && data.errorMsg" class="node-error-tooltip" :title="data.errorMsg">
      {{ data.errorMsg }}
    </div>

    <!-- 完成状态指示（独立于图标区） -->
    <span v-if="data.status === 'completed'" class="status-badge completed">✓</span>
    <!-- 多结果角标 -->
    <span v-if="data.status === 'completed' && resultCount > 1" class="result-count-badge">×{{ resultCount }}</span>

    <!-- ===== 单结果：完全覆盖节点框 ===== -->
    <img
      v-if="resolvedOutputUrl && data.status === 'completed' && !isVideoOutput && !hasMultiResults"
      :src="resolvedOutputUrl"
      alt="result"
      class="result-cover"
      @error="(e: any) => console.error('[CapabilityNode] img error:', e)"
    />
    <video
      v-else-if="resolvedOutputUrl && data.status === 'completed' && isVideoOutput && !hasMultiResults"
      :src="resolvedOutputUrl"
      alt="result"
      class="result-cover"
      autoplay
      loop
      muted
      playsinline
      @error="(e: any) => console.error('[CapabilityNode] video error:', e)"
    />

    <!-- ===== 多结果：缩略图网格 ===== -->
    <div v-if="hasMultiResults && data.status === 'completed'" class="result-grid">
      <div
        v-for="(url, idx) in resolvedGridUrls"
        :key="idx"
        class="grid-thumb"
        :class="{ selected: selectedIndex === idx }"
        @click.stop="selectImage(idx)"
        @contextmenu.prevent.stop="onThumbContext($event, idx)"
      >
        <img
          v-if="url && !isVideoOutput"
          :src="url"
          alt="结果 {{ idx + 1 }}"
          @error="(e: any) => console.error('[CapabilityNode] grid img error:', idx, e)"
        />
        <video
          v-else-if="url && isVideoOutput"
          :src="url"
          autoplay loop muted playsinline
          @error="(e: any) => console.error('[CapabilityNode] grid video error:', idx, e)"
        />
        <div v-else class="grid-loading">···</div>
        <span class="grid-index">{{ idx + 1 }}</span>
      </div>
    </div>

    <!-- 输入端口：绝对定位的 hover 区域 -->
    <div v-for="(slot, idx) in data.inputSlots" :key="'in-' + slot.name"
      class="port-zone input-zone"
      :class="{ 'port-first': idx === 0, 'port-optional': !slot.required }"
      :style="{ '--port-color': getPortColor(slot.dataType), top: handleTop(data.inputSlots!.length, idx) }"
    >
      <Handle
        :id="slot.name"
        type="target"
        :position="Position.Left"
        :class="{ 'handle-required': slot.required, 'handle-optional': !slot.required }"
      />
      <span class="port-bubble left-bubble">{{ slot.displayName || PORT_TYPE_NAMES[slot.dataType] || slot.dataType }}</span>
    </div>

    <!-- 输出端口 -->
    <div v-for="(slot, idx) in data.outputSlots" :key="'out-' + slot.name"
      class="port-zone output-zone"
      :class="{ 'port-first': idx === 0 }"
      :style="{ '--port-color': getPortColor(slot.dataType), top: handleTop(data.outputSlots!.length, idx) }"
    >
      <Handle
        :id="slot.name"
        type="source"
        :position="Position.Right"
      />
      <span class="port-bubble right-bubble">{{ slot.displayName || PORT_TYPE_NAMES[slot.dataType] || slot.dataType }}</span>
    </div>


  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted, nextTick } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import type { FlowNodeData } from '~/composables/useFlowCanvas'
import { PORT_TYPE_COLORS, CATEGORY_ACCENTS, PORT_TYPE_NAMES } from '~/types/flow'
import { getNodeIcon } from './FlowIcons'

const props = defineProps<{
  id: string
  data: FlowNodeData
  selected: boolean
}>()

// 节点结果缩略图
const mediaLoader = useMediaLoader()
const resolvedOutputUrl = ref('')
const resolvedGridUrls = ref<string[]>([])
const { updateNodeInternals } = useVueFlow()

// 判断节点输出是否为视频类型
const isVideoOutput = computed(() => {
  const type = props.data.type
  return type === 'image_to_video' || type === 'text_to_video' || type === 'video_to_video' || type === 'first_last_frame' || type === 'reference_to_video' || type === 'video_extend' || type === 'video_edit' || type === 'omni_video'
})

// 多结果数量
const resultCount = computed(() => {
  const urls = (props.data as any).outputUrls
  return urls ? urls.length : (props.data.outputUrl ? 1 : 0)
})

// 是否多结果
const hasMultiResults = computed(() => resultCount.value > 1)

// 选中索引（从 params 读取，默认 0）
const selectedIndex = computed(() => {
  return (props.data.params as any).selectedIndex ?? 0
})

// 加载单个 URL 的 helper
async function resolveUrl(url: string): Promise<string> {
  if (!url) return ''
  const match = url.match(/\/api\/v1\/media\/(\d+)\/file/)
  if (match) {
    try {
      return await mediaLoader.loadMedia({ id: Number(match[1]), url })
    } catch {
      return url
    }
  }
  return url
}

// 单结果 watch
watch(() => props.data.outputUrl, async (url) => {
  if (!url || hasMultiResults.value) {
    resolvedOutputUrl.value = ''
    return
  }
  resolvedOutputUrl.value = await resolveUrl(url)
  await nextTick()
  updateNodeInternals(props.id)
}, { immediate: true })

// 多结果 watch
watch(() => (props.data as any).outputUrls, async (urls) => {
  if (!urls || urls.length <= 1) {
    resolvedGridUrls.value = []
    return
  }
  // 先填充占位
  resolvedGridUrls.value = urls.map(() => '')
  // 异步加载
  const resolved: string[] = []
  for (const url of urls) {
    resolved.push(await resolveUrl(url))
  }
  resolvedGridUrls.value = resolved
  await nextTick()
  updateNodeInternals(props.id)
}, { immediate: true })

// 选中图片
function selectImage(idx: number) {
  ;(props.data.params as any).selectedIndex = idx
}

// 右键缩略图 → 通知 FlowCanvas 创建引用节点
function onThumbContext(event: MouseEvent, idx: number) {
  // 通过自定义事件向上传递
  const customEvent = new CustomEvent('create-ref-node', {
    detail: {
      parentNodeId: props.id,
      imageIndex: idx,
      x: event.clientX,
      y: event.clientY,
    },
    bubbles: true,
  })
  ;(event.target as HTMLElement).dispatchEvent(customEvent)
}

onUnmounted(() => {
  mediaLoader.cleanup()
})

const categoryAccent = computed(() =>
  CATEGORY_ACCENTS[props.data.category] || '#64748b'
)

const nodeIcon = computed(() =>
  getNodeIcon(props.data.type, props.data.category)
)

/** 计算端口纵向位置：1个居中，多个均匀分布 */
function handleTop(total: number, idx: number): string {
  if (total <= 1) return '50%'
  const offset = idx - (total - 1) / 2
  return `calc(50% + ${offset * 20}px)`
}

function getPortColor(dataType: string): string {
  return PORT_TYPE_COLORS[dataType as keyof typeof PORT_TYPE_COLORS] || '#64748b'
}
</script>

<style scoped>
.capability-node {
  position: relative;
  background: var(--flow-node-bg);
  border: 1.5px solid var(--flow-border);
  border-radius: 10px;
  width: 96px;
  min-height: 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px;
  color: var(--flow-text);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: border-color 0.25s, box-shadow 0.25s, transform 0.15s, width 0.3s;
}

/* 多结果时节点变大 */
.capability-node.multi-result {
  width: 180px;
  min-height: 180px;
  padding: 4px;
}

.capability-node:hover {
  border-color: var(--accent, var(--flow-accent));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.capability-node.selected {
  border-color: var(--accent, var(--flow-accent));
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--accent, #3b82f6) 20%, transparent),
    0 4px 12px rgba(0, 0, 0, 0.2);
}

.capability-node.status-running {
  border-color: #f59e0b;
  animation: pulse-glow 2s ease-in-out infinite;
}

.capability-node.status-completed {
  border-color: #22c55e;
  box-shadow: 0 0 10px rgba(34, 197, 94, 0.1);
}

.capability-node.status-failed {
  border-color: #ef4444;
  box-shadow: 0 0 10px rgba(239, 68, 68, 0.15);
}

@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 8px rgba(245, 158, 11, 0.12); }
  50% { box-shadow: 0 0 20px rgba(245, 158, 11, 0.3); }
}

/* 图标区 */
.node-icon-area {
  position: relative;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.node-svg {
  width: 24px;
  height: 24px;
  color: var(--flow-icon);
  transition: color 0.25s;
}

.capability-node:hover .node-svg {
  color: var(--flow-icon-hover);
}

.status-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid var(--flow-bg-deep);
}

.status-dot.running {
  background: #f59e0b;
  animation: blink 1s infinite;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.5);
}

.status-dot.failed {
  background: #ef4444;
}

.node-error-tooltip {
  position: absolute;
  bottom: -28px;
  left: 50%;
  transform: translateX(-50%);
  background: #ef4444;
  color: white;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  white-space: nowrap;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: auto;
  z-index: 10;
  cursor: help;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* ==================== 端口系统 ==================== */

.port-zone {
  position: absolute;
  width: 24px;
  height: 24px;
  transform: translateY(-50%);
  z-index: 5;
  cursor: crosshair;
  display: flex;
  align-items: center;
  justify-content: center;
}

.input-zone {
  left: -18px;
}

.output-zone {
  right: -18px;
}

.port-zone:not(.port-first) {
  opacity: 0;
  pointer-events: none;
}

.port-zone.port-first {
  opacity: 1;
  pointer-events: auto;
}

.capability-node:hover .port-zone {
  opacity: 1;
  pointer-events: auto;
}

.port-zone:has(.vue-flow__handle.valid),
.port-zone:has(.vue-flow__handle.connecting) {
  opacity: 1 !important;
  pointer-events: auto !important;
}

.port-zone :deep(.vue-flow__handle) {
  position: absolute !important;
  top: 50% !important;
  left: 50% !important;
  right: auto !important;
  bottom: auto !important;
  transform: translate(-50%, -50%) !important;
  width: 10px !important;
  height: 10px !important;
  border: 2px solid var(--flow-bg-deep) !important;
  background: var(--port-color, #64748b) !important;
  z-index: 3;
  transition: all 0.15s;
}

.handle-required {
  background: var(--port-color) !important;
  box-shadow: 0 0 6px color-mix(in srgb, var(--port-color, #64748b) 40%, transparent) !important;
}

.handle-optional {
  background: transparent !important;
  box-shadow: none !important;
  border-color: var(--flow-text-dim) !important;
}

.port-bubble {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 7px;
  line-height: 1;
  color: var(--port-color, var(--flow-text-muted));
  background: var(--flow-surface);
  border: none;
  border-radius: 2px;
  padding: 1px 3px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s;
  z-index: 10;
}

.left-bubble {
  right: calc(100% + 6px);
  text-align: right;
}

.right-bubble {
  left: calc(100% + 6px);
  text-align: left;
}

.port-zone:hover .port-bubble {
  opacity: 1;
}

/* ==================== 单结果缩略图 ==================== */
.result-cover {
  position: absolute;
  top: 3px;
  left: 3px;
  right: 3px;
  bottom: 3px;
  width: calc(100% - 6px);
  height: calc(100% - 6px);
  object-fit: cover;
  border-radius: 7px;
  z-index: 1;
}

/* ==================== 多结果缩略图网格 ==================== */
.result-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  width: 100%;
  height: 100%;
  padding: 2px;
  z-index: 1;
}

.grid-thumb {
  position: relative;
  border-radius: 4px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.05);
  aspect-ratio: 1;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.15s, transform 0.1s;
}

.grid-thumb:hover {
  border-color: rgba(59, 130, 246, 0.5);
  transform: scale(1.05);
}

.grid-thumb.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.4);
}

.grid-thumb img,
.grid-thumb video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.grid-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--flow-text-muted, #888);
  font-size: 10px;
}

.grid-index {
  position: absolute;
  top: 1px;
  left: 1px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  font-size: 8px;
  font-weight: 600;
  padding: 1px 3px;
  border-radius: 3px;
  pointer-events: none;
}

/* ==================== 角标 ==================== */
.status-badge.completed {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 16px;
  height: 16px;
  background: #22c55e;
  border-radius: 50%;
  border: 1.5px solid var(--flow-bg-deep);
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: bold;
  color: var(--flow-bg-deep);
  line-height: 1;
}

.result-count-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  background: #3b82f6;
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 5px;
  border-radius: 8px;
  z-index: 6;
  pointer-events: none;
}

</style>