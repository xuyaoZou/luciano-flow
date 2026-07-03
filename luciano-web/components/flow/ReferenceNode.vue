<!-- 引用节点 — 指向父节点的特定结果，可独立连线 -->
<template>
  <div
    class="reference-node"
    :class="{ selected }"
    :style="{ '--accent': '#a855f7' }"
  >
    <!-- 缩略图 -->
    <img
      v-if="resolvedUrl && !isVideo"
      :src="resolvedUrl"
      alt="ref"
      class="ref-thumb"
      @error="(e: any) => console.error('[RefNode] img error:', e)"
    />
    <video
      v-else-if="resolvedUrl && isVideo"
      :src="resolvedUrl"
      class="ref-thumb"
      autoplay loop muted playsinline
      @error="(e: any) => console.error('[RefNode] video error:', e)"
    />
    <div v-else class="ref-loading">···</div>

    <!-- 来源标签 -->
    <span class="ref-source">{{ parentNodeId?.slice(-4) }}#{{ imageIndex + 1 }}</span>

    <!-- 输出端口 -->
    <div class="port-zone output-zone port-first">
      <Handle
        id="output"
        type="source"
        :position="Position.Right"
      />
      <span class="port-bubble right-bubble">{{ portLabel }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted, computed } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { useMediaLoader } from '~/composables/useMediaLoader'

const props = defineProps<{
  id: string
  data: any
  selected: boolean
}>()

const mediaLoader = useMediaLoader()
const resolvedUrl = ref('')
const { updateNodeInternals } = useVueFlow()

const isVideo = computed(() => {
  const ptype = props.data.params?.parentType || ''
  return ptype === 'image_to_video' || ptype === 'text_to_video' || ptype === 'video_to_video' || ptype === 'first_last_frame'
})

const parentNodeId = computed(() => props.data.params?.parentNodeId)
const imageIndex = computed(() => props.data.params?.imageIndex ?? 0)
const portLabel = computed(() => isVideo.value ? 'video' : 'image')

// 从父节点取对应索引的 URL
watch(
  () => [props.data.params?.parentNodeId, props.data.params?.imageIndex, props.data.params?.parentUrls],
  async () => {
    const urls = props.data.params?.parentUrls
    const idx = props.data.params?.imageIndex ?? 0
    if (!urls || !urls[idx]) {
      resolvedUrl.value = ''
      return
    }
    const url = urls[idx]
    const match = url?.match(/\/api\/v1\/media\/(\d+)\/file/)
    if (match) {
      try {
        resolvedUrl.value = await mediaLoader.loadMedia({ id: Number(match[1]), url })
      } catch {
        resolvedUrl.value = url
      }
    } else {
      resolvedUrl.value = url || ''
    }
  },
  { immediate: true, deep: true }
)

onUnmounted(() => {
  mediaLoader.cleanup()
})
</script>

<style scoped>
.reference-node {
  position: relative;
  background: var(--flow-node-bg);
  border: 1.5px solid #a855f7;
  border-radius: 8px;
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  /* overflow: hidden 会裁掉 Handle，改用 clip-path 只裁圆角 */
  box-shadow: 0 2px 8px rgba(168, 85, 247, 0.15);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.reference-node:hover {
  border-color: #c084fc;
  box-shadow: 0 0 12px rgba(168, 85, 247, 0.3);
}

.reference-node.selected {
  border-color: #c084fc;
  box-shadow: 0 0 0 2px rgba(168, 85, 247, 0.2), 0 4px 12px rgba(0, 0, 0, 0.2);
}

.ref-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 6px;
}

.ref-loading {
  color: var(--flow-text-muted, #888);
  font-size: 12px;
}

.ref-source {
  position: absolute;
  bottom: 1px;
  left: 1px;
  background: rgba(0, 0, 0, 0.6);
  color: #c084fc;
  font-size: 7px;
  font-weight: 600;
  padding: 1px 3px;
  border-radius: 3px;
  pointer-events: none;
}

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

.output-zone {
  right: -18px;
  top: 50%;
}

.port-zone :deep(.vue-flow__handle) {
  position: absolute !important;
  top: 50% !important;
  left: 50% !important;
  transform: translate(-50%, -50%) !important;
  width: 10px !important;
  height: 10px !important;
  border: 2px solid var(--flow-bg-deep) !important;
  background: #a855f7 !important;
  z-index: 10 !important;
  cursor: crosshair;
}

.port-bubble {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 7px;
  line-height: 1;
  color: #a855f7;
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

.right-bubble {
  left: calc(100% + 6px);
}

.port-zone:hover .port-bubble {
  opacity: 1;
}
</style>