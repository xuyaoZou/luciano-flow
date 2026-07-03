<!-- 资源库 — Apple Photos 风格，视觉优先 -->
<template>
  <div class="flex flex-col h-full">
    <!-- 顶栏 -->
    <div class="flex items-center justify-between px-6 py-4">
      <div class="flex items-center gap-3">
        <h2 class="text-lg font-semibold">资源库</h2>
        <span class="text-xs text-luciano-muted">{{ assets.length }} 项</span>
      </div>
      <!-- 筛选 -->
      <div class="flex items-center gap-2">
        <FilterChip
          v-for="filter in filters"
          :key="filter.key"
          :label="filter.label"
          :active="activeFilter === filter.key"
          @click="activeFilter = activeFilter === filter.key ? 'all' : filter.key"
        />
      </div>
    </div>

    <!-- 网格 -->
    <div class="flex-1 overflow-y-auto px-6 pb-6">
      <div v-if="filteredAssets.length === 0" class="flex flex-col items-center justify-center h-64 text-center animate-fade-in">
        <div class="text-4xl mb-3">📁</div>
        <p class="text-luciano-muted text-sm">还没有资源</p>
        <p class="text-luciano-muted/60 text-xs mt-1">通过 Agent 对话生成，或手动上传</p>
      </div>

      <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
        <div
          v-for="asset in filteredAssets"
          :key="asset.id"
          class="group cursor-pointer animate-scale-in"
          @click="previewAsset = asset"
        >
          <div class="relative bg-luciano-card rounded-xl overflow-hidden border border-luciano-border/30 hover:border-luciano-border transition-all group-hover:shadow-lg group-hover:shadow-black/20">
            <!-- 图片 -->
            <div v-if="asset.mediaType === 'image'" class="aspect-[3/4]">
              <img
                :src="getBlobUrl(asset.id, asset.url)"
                :alt="'媒体 ' + asset.id"
                class="w-full h-full object-contain"
                loading="lazy"
              />
            </div>
            <!-- 视频 -->
            <div v-else class="aspect-square bg-luciano-card flex items-center justify-center relative">
              <div class="text-3xl opacity-60">🎬</div>
              <div class="absolute bottom-1.5 right-1.5 w-6 h-6 rounded-full bg-black/60 flex items-center justify-center">
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="white"><path d="M8 5v14l11-7z"/></svg>
              </div>
            </div>

            <!-- 来源标记 -->
            <span v-if="asset.source === 'agent'" class="absolute top-1.5 left-1.5 text-[9px] bg-black/60 backdrop-blur-md text-white px-1.5 py-0.5 rounded-full">
              AI
            </span>
          </div>
          <!-- 名称 -->
          <p class="mt-1.5 text-xs text-luciano-muted truncate px-0.5">
            {{ asset.source === 'agent' ? 'AI 生成' : '上传' }} · {{ formatTime(asset.createdAt) }}
          </p>
        </div>
      </div>
    </div>

    <!-- Lightbox 预览 -->
    <Lightbox
      v-if="previewAsset"
      :asset="previewAsset"
      :project-id="projectId"
      @close="previewAsset = null"
    />
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  projectId: number
}>()

const { getMediaAssets } = useApi()
const { loadMediaList, getBlobUrl, cleanup } = useMediaLoader()
const { data: assets } = await useAsyncData(() => getMediaAssets(props.projectId))

// 数据加载后预加载图片 blob URL
watch(assets, async (list) => {
  if (list && Array.isArray(list)) {
    await loadMediaList(list.filter((a: any) => a.mediaType === 'image'))
  }
}, { immediate: true })

const activeFilter = ref('all')
const previewAsset = ref<any>(null)

const filters = [
  { key: 'image', label: '图片' },
  { key: 'video', label: '视频' },
  { key: 'agent', label: 'AI 生成' },
]

const filteredAssets = computed(() => {
  if (activeFilter.value === 'all') return assets.value || []
  if (activeFilter.value === 'agent') return (assets.value || []).filter((a: any) => a.source === 'agent')
  return (assets.value || []).filter((a: any) => a.mediaType === activeFilter.value)
})

const formatTime = (date: string) => {
  if (!date) return ''
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

onUnmounted(() => cleanup())
</script>