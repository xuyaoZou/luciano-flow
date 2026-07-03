<!-- 资产页 — 纯资源库 -->
<template>
  <div class="flex flex-col h-full relative">
    <!-- 未登录遮罩 -->
    <div v-if="requireAuth" class="absolute inset-0 z-10 bg-luciano-bg/80 backdrop-blur-sm flex flex-col items-center justify-center rounded-xl">
      <div class="text-center">
        <div class="text-4xl mb-3">🔒</div>
        <p class="text-sm text-luciano-muted mb-4">登录后查看资产</p>
        <button @click="navigateTo('/login')" class="px-5 py-2 bg-apple-blue text-white text-sm rounded-xl hover:bg-apple-blue/90 transition-colors">去登录</button>
      </div>
    </div>
    <!-- 标题 -->
    <div class="px-6 pt-6 pb-2">
      <h1 class="text-xl font-semibold">资产</h1>
      <p class="text-xs text-luciano-muted mt-1">AI 生成的所有图片和视频资源</p>
    </div>

    <!-- 筛选 -->
    <div class="px-6 pb-3 flex items-center gap-2">
      <FilterChip label="全部" :active="filter === ''" @click="filter = ''" />
      <FilterChip label="图片" :active="filter === 'image'" @click="filter = 'image'" />
      <FilterChip label="视频" :active="filter === 'video'" @click="filter = 'video'" />
    </div>

    <!-- 资源网格 -->
    <div class="flex-1 overflow-y-auto px-6 pb-6">
      <div v-if="assets.length === 0" class="flex flex-col items-center justify-center h-full text-luciano-muted">
        <div class="text-3xl mb-3">🗃</div>
        <p class="text-sm">暂无资产</p>
        <p class="text-xs mt-1">在创作页生成图片后自动归入资源库</p>
      </div>
      <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
        <div
          v-for="asset in filteredAssets"
          :key="asset.id"
          class="group relative rounded-xl overflow-hidden bg-luciano-card border border-luciano-border/20 hover:border-luciano-border transition-all"
        >
          <!-- 媒体容器 -->
          <div class="aspect-[3/4] overflow-hidden relative">
            <!-- 图片 -->
            <img v-if="asset.mediaType === 'image'" :src="getBlobUrl(asset.id, asset.url)" class="w-full h-full object-contain cursor-pointer" loading="lazy" @click="openLightbox(asset)" />
            <!-- 视频：内联播放 + 首帧预览 -->
            <template v-else>
              <video
                :src="getVideoUrl(asset)"
                class="w-full h-full object-contain cursor-pointer"
                preload="metadata"
                :poster="asset.thumbnailUrl || undefined"
                controls
                @click.stop
              />
            </template>
          </div>
          <!-- 类型标签 -->
          <span class="absolute top-2 left-2 text-[9px] bg-black/60 backdrop-blur-md text-white px-1.5 py-0.5 rounded-full pointer-events-none">
            {{ asset.mediaType === 'image' ? '图片' : '视频' }}
          </span>
          <!-- Hover 来源信息 -->
          <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent p-3 opacity-0 group-hover:opacity-100 transition-opacity duration-200 pointer-events-none">
            <p v-if="asset.source" class="text-[10px] text-white/80 truncate">来源：{{ asset.source }}</p>
            <p v-if="asset.conversationTitle" class="text-[10px] text-white/60 truncate mt-0.5">{{ asset.conversationTitle }}</p>
            <p v-if="asset.createdAt" class="text-[10px] text-white/40 mt-0.5">{{ formatDate(asset.createdAt) }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Lightbox — 仅图片用 -->
    <Lightbox
      v-if="lightboxAsset && lightboxAsset.mediaType === 'image'"
      :asset="lightboxAsset"
      :projectId="props.projectId || 0"
      @close="lightboxAsset = null"
    />
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ projectId?: number | null; requireAuth?: boolean }>()

const { getMediaAssets } = useApi()
const { loadMediaList, getBlobUrl, cleanup } = useMediaLoader()
const config = useRuntimeConfig()

const assets = ref<any[]>([])
const filter = ref('')
const lightboxAsset = ref<any>(null)

// Token 用于视频 URL
const authToken = useState<string>('auth-token', () => '')

/** 视频使用落地文件 URL + token 参数 */
const getVideoUrl = (asset: any) => {
  if (!asset?.id) return asset?.url || ''
  const base = config.public.apiBase || '/api/v1'
  const t = authToken.value || ''
  return `${base}/media/${asset.id}/file${t ? '?token=' + t : ''}`
}

const filteredAssets = computed(() => {
  if (!filter.value) return assets.value
  return assets.value.filter(a => a.mediaType === filter.value)
})

const loadAssets = async () => {
  // 确保 token 从 cookie 同步
  const tokenCookie = useCookie('luciano-token', { maxAge: 60 * 60 * 24 * 7 })
  if (!authToken.value && tokenCookie.value) {
    authToken.value = tokenCookie.value
  }

  try {
    // 不传 projectId = 查当前用户所有资产
    const list = await getMediaAssets(null)
    if (list && Array.isArray(list)) {
      assets.value = list
      // 只预加载图片的 blob URL，视频直接用原始 URL
      await loadMediaList(list.filter(a => a.mediaType === 'image'))
    }
  } catch { /* 静默 */ }
}

const openLightbox = (asset: any) => {
  lightboxAsset.value = asset
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

watch(() => props.projectId, () => {
  loadAssets()
}, { immediate: true })

// 切换到资产页时重新加载（projectId 可能没变，但数据更新了）
onActivated(() => {
  if (props.projectId) loadAssets()
})

onUnmounted(() => cleanup())
</script>