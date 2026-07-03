<!-- 特殊节点 — 输入/预览/控制节点，纯图标风格 -->
<template>
  <div
    class="special-node"
    :class="[
      `type-${data.type.toLowerCase()}`,
      data.status || 'idle',
      selected ? 'selected' : '',
    ]"
    :style="{ '--accent': nodeAccentColor }"
  >
    <!-- 图标区（ElementSource 有图片时隐藏） -->
    <div v-if="!(data.type === 'ElementSource' && data.params?.elementThumb)" class="node-icon-area">
      <svg class="node-svg" viewBox="0 0 24 24" fill="currentColor">
        <path :d="nodeIcon" />
      </svg>
    </div>

    <!-- 输入节点：URL/文字预览 -->
    <div v-if="isInputNode && data.params" class="node-content">
      <div v-if="data.params.url && data.type === 'ImageInput'" class="input-preview">
        <img :src="data.params.url" alt="" class="input-thumb" />
      </div>
      <div v-else-if="data.params.url && data.type === 'VideoInput'" class="input-preview">
        <video :src="data.params.url" class="input-thumb" muted />
      </div>
      <div v-else-if="data.params.text" class="text-preview">
        <span>{{ truncate(data.params.text, 40) }}</span>
      </div>
    </div>
    <!-- 主体节点：图片占满节点框，右上角固定标签 -->
    <div v-else-if="data.type === 'ElementSource' && data.params?.elementThumb" class="element-preview">
      <img :src="data.params.elementThumb" alt="" class="input-thumb" />
      <span class="element-corner-tag">主体</span>
    </div>
    <div v-else-if="data.type === 'ElementSource'" class="element-pending">
        <span class="element-hint">点击配置主体</span>
    </div>

    <!-- 预览节点：生成结果 -->
    <div v-if="isOutputNode && data.outputUrl" class="node-result">
      <img v-if="data.type === 'ImagePreview'" :src="data.outputUrl" alt="" class="preview-thumb" />
      <video v-else-if="data.type === 'VideoPreview'" :src="data.outputUrl" class="preview-thumb" controls />
    </div>

    <!-- 输入端口：绝对定位 -->
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
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { FlowNodeData } from '~/composables/useFlowCanvas'
import { PORT_TYPE_COLORS, CATEGORY_ACCENTS, PORT_TYPE_NAMES } from '~/types/flow'
import { getNodeIcon, SPECIAL_ICONS } from './FlowIcons'

const props = defineProps<{
  id: string
  data: FlowNodeData
  selected: boolean
}>()

const isInputNode = computed(() =>
  ['ImageInput', 'VideoInput', 'AudioInput', 'TextInput'].includes(props.data.type)
)
const isOutputNode = computed(() =>
  ['ImagePreview', 'VideoPreview'].includes(props.data.type)
)

const nodeAccentColor = computed(() => {
  const colors: { [key: string]: string } = {
    ImageInput: '#22c55e',
    VideoInput: '#22c55e',
    AudioInput: '#22c55e',
    TextInput: '#22c55e',
    ImagePreview: '#3b82f6',
    VideoPreview: '#3b82f6',
    Switch: '#a855f7',
    ElementSource: '#FF6B6B',
  }
  return colors[props.data.type] || '#64748b'
})

const nodeIcon = computed(() =>
  SPECIAL_ICONS[props.data.type] || getNodeIcon(props.data.type)
)

function handleTop(total: number, idx: number): string {
  if (total <= 1) return '50%'
  const offset = idx - (total - 1) / 2
  return `calc(50% + ${offset * 20}px)`
}

function getPortColor(dataType: string): string {
  return PORT_TYPE_COLORS[dataType as keyof typeof PORT_TYPE_COLORS] || '#64748b'
}

function truncate(str: string, len: number): string {
  return str.length > len ? str.slice(0, len) + '...' : str
}
</script>

<style scoped>
.special-node {
  position: relative;
  background: var(--flow-node-bg);
  border: 1.5px solid var(--flow-border);
  border-radius: 10px;
  min-width: 72px;
  min-height: 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px;
  color: var(--flow-text);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: border-color 0.25s, box-shadow 0.25s, transform 0.15s;
}

.special-node:hover {
  border-color: var(--accent, var(--flow-accent));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.special-node.selected {
  border-color: var(--accent, var(--flow-accent));
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--accent, #3b82f6) 20%, transparent),
    0 4px 12px rgba(0, 0, 0, 0.2);
}

/* 输入节点 — 翠绿 */
.special-node.type-imageinput,
.special-node.type-videoinput,
.special-node.type-audioinput,
.special-node.type-textinput {
  --accent: #22c55e;
}

/* 预览节点 — 蓝色 */
.special-node.type-imagepreview,
.special-node.type-videopreview {
  --accent: #3b82f6;
}

/* 图标区 */
.node-icon-area {
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

.special-node:hover .node-svg {
  color: var(--flow-icon-hover);
}

/* 输入内容预览 */
.node-content {
  padding: 2px 0;
  max-width: 64px;
}

.input-preview {
  margin: 2px 0;
}

.input-thumb {
  width: 100%;
  max-height: 48px;
  object-fit: cover;
  border-radius: 3px;
  border: 1px solid var(--flow-border);
}

.text-preview {
  font-size: 9px;
  color: var(--flow-text-dim);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 主体节点 */
.special-node.type-elementsource {
  --accent: #FF6B6B;
  width: 96px;
  min-height: 72px;
}

.element-preview {
  position: absolute;
  top: 3px;
  left: 3px;
  right: 3px;
  bottom: 3px;
  border-radius: 7px;
  overflow: hidden;
  z-index: 1;
}

.element-preview .input-thumb {
  width: 100%;
  height: 100%;
  max-height: none;
  object-fit: cover;
  border-radius: 7px;
  border: none;
}

.element-corner-tag {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 7px;
  padding: 1px 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.6);
  color: #FF6B6B;
  white-space: nowrap;
  z-index: 3;
}

.element-pending {
  font-size: 8px;
  color: var(--flow-text-dim);
}

/* 预览节点结果 */
.node-result {
  max-width: 64px;
  margin-top: 2px;
}

.preview-thumb {
  width: 100%;
  max-height: 80px;
  object-fit: contain;
  border-radius: 3px;
  border: 1px solid var(--flow-border);
}

/* ==================== 端口系统 ==================== */

/* 端口 hover 区域 — 绝对定位，不影响节点布局 */
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

/* 默认：只显示第一个端口 */
.port-zone:not(.port-first) {
  opacity: 0;
  pointer-events: none;
}

.port-zone.port-first {
  opacity: 1;
  pointer-events: auto;
}

/* hover 节点时：所有端口可见 */
.special-node:hover .port-zone {
  opacity: 1;
  pointer-events: auto;
}

/* 连线拖拽时：valid/connecting 端口也显示 */
.port-zone:has(.vue-flow__handle.valid),
.port-zone:has(.vue-flow__handle.connecting) {
  opacity: 1 !important;
  pointer-events: auto !important;
}

/* Handle — 完全覆盖 VueFlow 默认样式 */
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

/* 必填端口：实心高亮 */
.handle-required {
  background: var(--port-color) !important;
  box-shadow: 0 0 6px color-mix(in srgb, var(--port-color, #64748b) 40%, transparent) !important;
}

/* 可选端口：空心半透明 */
.handle-optional {
  background: transparent !important;
  box-shadow: none !important;
  border-color: var(--flow-text-dim) !important;
}

/* 端口气泡标签 */
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

/* 输入端口气泡 — 在节点框左侧外面 */
.left-bubble {
  right: calc(100% + 6px);
  text-align: right;
}

/* 输出端口气泡 — 在节点框右侧外面 */
.right-bubble {
  left: calc(100% + 6px);
  text-align: left;
}

.port-zone:hover .port-bubble {
  opacity: 1;
}


</style>