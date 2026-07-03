<!-- 关联浮层 — 从资源库选媒体关联到专业模式资产 -->
<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 flex items-center justify-center" @click.self="$emit('close')">
      <div class="absolute inset-0 bg-black/40 backdrop-blur-sm animate-fade-in" @click="$emit('close')" />
      <div class="relative z-10 w-80 bg-luciano-card rounded-2xl border border-luciano-border/50 p-5 shadow-2xl animate-scale-in">
        <h3 class="text-sm font-semibold mb-4">关联到项目</h3>

        <!-- 缩略图 -->
        <div class="w-full aspect-video rounded-xl overflow-hidden bg-luciano-bg mb-4">
          <img v-if="media.mediaType === 'image'" :src="imageUrl" class="w-full h-full object-contain" />
        </div>

        <!-- 选择目标 -->
        <div class="space-y-2 mb-4">
          <button
            v-for="target in linkTargets"
            :key="target.type"
            @click="selectedType = target.type"
            class="w-full flex items-center gap-3 p-2.5 rounded-xl text-left transition-all"
            :class="selectedType === target.type
              ? 'bg-apple-blue/10 border border-apple-blue/30'
              : 'bg-luciano-bg border border-transparent hover:border-luciano-border/50'"
          >
            <span>{{ target.icon }}</span>
            <span class="text-xs font-medium">{{ target.label }}</span>
          </button>
        </div>

        <!-- 操作 -->
        <div class="flex gap-2">
          <button @click="$emit('close')" class="flex-1 py-2 text-xs text-luciano-muted rounded-xl hover:bg-luciano-bg transition-colors">
            取消
          </button>
          <button
            @click="doLink"
            :disabled="!selectedType || isLinking"
            class="flex-1 py-2 text-xs font-medium bg-apple-blue text-white rounded-xl disabled:opacity-30 hover:bg-apple-blue/90 active:scale-95 transition-all"
          >
            {{ isLinking ? '关联中...' : '关联' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
const props = defineProps<{
  media: any
  projectId: number
}>()

const emit = defineEmits<{
  close: []
  linked: [mediaAssetId: number, assetType: string]
}>()

const { linkMediaAsset } = useApi()
const { loadMedia, getBlobUrl } = useMediaLoader()
const selectedType = ref<string | null>(null)
const isLinking = ref(false)
const imageUrl = ref(props.media?.url || '')

onMounted(async () => {
  if (props.media?.id) {
    await loadMedia(props.media)
    imageUrl.value = getBlobUrl(props.media.id, props.media.url)
  }
})

const linkTargets = [
  { type: 'character', icon: '👤', label: '角色' },
  { type: 'scene', icon: '🏞️', label: '场景' },
  { type: 'prop', icon: '📦', label: '道具' },
  { type: 'storyboard_first_frame', icon: '🎞️', label: '分镜首帧' },
  { type: 'storyboard_last_frame', icon: '🎞️', label: '分镜尾帧' },
]

const doLink = async () => {
  if (!selectedType.value || isLinking.value) return
  isLinking.value = true
  try {
    // TODO: 需要选择具体的角色/场景/道具 ID
    // 当前简化实现：关联到 media asset 本身
    await linkMediaAsset(props.media.id, selectedType.value, 0)
    emit('linked', props.media.id, selectedType.value)
  } catch (e) {
    console.error('[LinkPopover] 关联失败', e)
  } finally {
    isLinking.value = false
  }
}
</script>