<!-- 连线资源选择器 — multi 端口的所有连线来源，可勾选+排序，不删除连线 -->
<template>
  <div class="conn-resource-list">
    <div v-if="readonly" class="conn-readonly-hint">只读模式 — 参数已锁定</div>
    <div class="conn-items" :class="{ 'readonly-mode': readonly }">
      <div
        v-for="(conn, idx) in connections"
        :key="conn.sourceNodeId + conn.sourceSlot"
        class="conn-item"
        :class="{ selected: isSelected(conn), 'readonly-item': readonly }"
        @click.stop="!readonly && toggleSelect(conn)"
      >
        <span class="conn-checkbox" :class="{ checked: isSelected(conn) }">
          <span v-if="isSelected(conn)" class="check-mark">✓</span>
        </span>
        <span class="conn-index">{{ idx + 1 }}</span>
        <div class="conn-thumb-wrapper">
          <img
            v-if="getThumbUrl(conn) && !isVideoConn(conn)"
            :src="getThumbUrl(conn)"
            alt=""
            class="conn-thumb"
            @error="(e: any) => { (e.target as HTMLImageElement).style.opacity = '0.3' }"
          />
          <video
            v-else-if="getThumbUrl(conn) && isVideoConn(conn)"
            :src="getThumbUrl(conn)"
            class="conn-thumb"
            muted
          />
          <div v-else class="conn-thumb-placeholder">···</div>
        </div>
        <div class="conn-info">
          <span class="conn-source-type">{{ getSourceLabel(conn) }}</span>
          <span class="conn-source-slot">{{ conn.sourceSlot || 'output' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { FlowNodeData } from '~/composables/useFlowCanvas'
import { useApi } from '~/composables/useApi'

interface ConnectionInfo {
  sourceNodeId: string
  sourceSlot: string
  targetSlot: string
  sourceNode?: FlowNodeData
}

const props = defineProps<{
  connections: ConnectionInfo[]
  /** 当前已选中的 URL 列表（从 params 读取） */
  selectedUrls: string[]
  /** 只读模式：只展示不可交互 */
  readonly?: boolean
}>()

const emit = defineEmits<{
  /** 用户勾选状态变化，返回新的 URL 数组 */
  'update:selectedUrls': [urls: string[]]
}>()

const { getMediaFileUrl } = useApi()

// 缓存：URL → blob URL
const blobUrlCache = ref<{ [key: string]: string }>({})

// 连线有值但 selectedUrls 为空时，自动全选
watch(() => [props.connections, props.selectedUrls] as const, ([conns, urls]) => {
  if (conns.length === 0) return
  const allUrls = conns.map(getUrl).filter((u): u is string => !!u)
  if (allUrls.length === 0) return
  // 空选或值不匹配（旧数据）时，自动全选
  if (urls.length === 0 || urls.some(u => !allUrls.includes(u))) {
    emit('update:selectedUrls', allUrls)
  }
}, { immediate: true })

function isVideoConn(conn: ConnectionInfo): boolean {
  const t = conn.sourceNode?.type || ''
  return t.includes('video') || conn.sourceNode?.outputSlots?.some(s => s.dataType === 'video')
}

function getRawUrl(conn: ConnectionInfo): string {
  const data = conn.sourceNode
  if (!data) return ''
  // ElementSource 主体节点：从 params 取图片
  if (data.type === 'ElementSource') {
    return data.params?.elementThumb || ''
  }
  if (data.outputUrls && data.outputUrls.length > 0) {
    return data.outputUrls[0]
  }
  if (data.outputUrl) {
    return data.outputUrl
  }
  return ''
}

/** 获取缩略图 URL（如果是 media proxy URL，异步加载为 blob URL） */
function getThumbUrl(conn: ConnectionInfo): string {
  const raw = getRawUrl(conn)
  if (!raw) return ''
  // 如果不是 media proxy URL，直接返回
  if (!raw.startsWith('/api/v1/media/')) return raw
  // 如果已缓存，返回 blob URL
  if (blobUrlCache.value[raw]) return blobUrlCache.value[raw]
  // 异步加载
  loadBlobUrl(raw)
  return ''  // 先返回空，加载完成后响应式更新
}

async function loadBlobUrl(proxyUrl: string) {
  try {
    // 拼接完整 URL：相对路径 + API base
    const config = useRuntimeConfig()
    const baseUrl = config.public.apiBase
    const apiOrigin = baseUrl.replace(/\/api\/v1$/, '')
    const fullUrl = apiOrigin + proxyUrl
    const tokenCookie = useCookie('luciano-token')
    const resp = await fetch(fullUrl, {
      headers: { Authorization: `Bearer ${tokenCookie.value || ''}` }
    })
    if (!resp.ok) return
    const blob = await resp.blob()
    const blobUrl = URL.createObjectURL(blob)
    blobUrlCache.value[proxyUrl] = blobUrl
  } catch (e) {
    console.error('[ConnectedResourceList] loadBlobUrl failed:', e)
  }
}

function getUrl(conn: ConnectionInfo): string {
  const data = conn.sourceNode
  if (!data) return ''
  // ElementSource: 值用 elementId，不是 thumbUrl
  if (data.type === 'ElementSource') {
    return data.params?.elementId?.toString() || ''
  }
  return getRawUrl(conn)
}

function isSelected(conn: ConnectionInfo): boolean {
  const url = getUrl(conn)
  if (!url) return false
  // 如果 selectedUrls 为空，默认全选
  if (props.selectedUrls.length === 0) return true
  return props.selectedUrls.includes(url)
}

function toggleSelect(conn: ConnectionInfo) {
  const url = getUrl(conn)
  if (!url) return
  const current = props.selectedUrls.length === 0
    ? props.connections.map(getUrl).filter((u): u is string => !!u) // 首次点击时先初始化为全选，再取消当前
    : [...props.selectedUrls]
  const idx = current.indexOf(url)
  if (idx >= 0) {
    current.splice(idx, 1)
  } else {
    current.push(url)
  }
  emit('update:selectedUrls', current)
}

function getSourceLabel(conn: ConnectionInfo): string {
  const data = conn.sourceNode
  if (!data) return conn.sourceNodeId.slice(-4)
  const typeMap: { [key: string]: string } = {
    text_to_image: '文生图',
    text_to_video: '文生视频',
    image_to_video: '图生视频',
    first_last_frame: '首尾帧',
    omni_image: 'Omni图',
    omni_video: 'Omni视频',
    reference: '引用',
    ImageInput: '图片输入',
    VideoInput: '视频输入',
    TextInput: '文本输入',
    ElementSource: '主体',
  }
  return typeMap[data.type] || data.type || conn.sourceNodeId.slice(-4)
}
</script>

<style scoped>
.conn-resource-list {
  width: 100%;
}

.conn-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.conn-count {
  font-size: 11px;
  color: #a855f7;
  font-weight: 600;
}

.conn-selected {
  font-size: 10px;
  color: var(--flow-text-dim);
}

.conn-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conn-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px;
  background: var(--flow-hover-bg);
  border: 1px solid var(--flow-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  opacity: 0.55;
}

.conn-item.selected {
  opacity: 1;
  border-color: #a855f7;
  background: rgba(168, 85, 247, 0.08);
}

.conn-item:hover {
  border-color: var(--flow-border-hover);
}

.conn-checkbox {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: 1.5px solid var(--flow-text-dim);
  border-radius: 3px;
  flex-shrink: 0;
  transition: all 0.15s;
}

.conn-checkbox.checked {
  background: #a855f7;
  border-color: #a855f7;
}

.check-mark {
  color: white;
  font-size: 10px;
  font-weight: bold;
}

.conn-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}

.conn-thumb-wrapper {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--flow-bg);
}

.conn-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.conn-thumb-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--flow-text-dim);
  font-size: 10px;
}

.conn-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
}

.conn-source-type {
  font-size: 11px;
  color: var(--flow-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conn-source-slot {
  font-size: 9px;
  color: var(--flow-text-dim);
}

.conn-readonly-hint {
  font-size: 10px;
  color: var(--flow-text-dim);
  margin-bottom: 6px;
  font-style: italic;
}

.conn-items.readonly-mode .conn-item {
  cursor: default;
}

.conn-item.readonly-item {
  cursor: default;
}
.conn-item.readonly-item:hover {
  border-color: var(--flow-border);
}
</style>