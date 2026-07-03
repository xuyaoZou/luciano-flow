<!-- 大图/视频预览 — Apple Photos 风格 -->
<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 flex items-center justify-center" @click.self="$emit('close')">
      <!-- 遮罩 -->
      <div class="absolute inset-0 bg-black/80 backdrop-blur-xl animate-fade-in" @click="$emit('close')" />

      <!-- 内容 -->
      <div class="relative z-10 max-w-4xl max-h-[85vh] w-full mx-6 animate-scale-in">
        <!-- 图片 -->
        <div v-if="asset.mediaType !== 'video'" class="rounded-2xl overflow-hidden bg-luciano-card">
          <img :src="imageUrl" :alt="'媒体'" class="w-full object-contain max-h-[70vh]" />
        </div>

        <!-- 视频 -->
        <div v-else class="rounded-2xl overflow-hidden bg-black">
          <video
            :src="videoUrl"
            class="w-full max-h-[70vh]"
            controls
            autoplay
            :poster="asset.thumbnailUrl || undefined"
            @click.stop
          />
        </div>

        <!-- 底部信息栏 -->
        <div class="flex items-center justify-between mt-3 px-1">
          <div class="text-xs text-luciano-muted">
            {{ asset.source === 'agent' ? 'AI 生成' : '上传' }}
            <span v-if="asset.mediaType"> · {{ asset.mediaType === 'image' ? '图片' : '视频' }}</span>
            <span v-if="asset.createdAt"> · {{ new Date(asset.createdAt).toLocaleDateString() }}</span>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="linkToProject"
              class="px-3 py-1.5 text-xs font-medium bg-apple-blue text-white rounded-full hover:bg-apple-blue/90 active:scale-95 transition-all"
            >
              关联到项目
            </button>
            <button
              @click="$emit('close')"
              class="w-8 h-8 rounded-full bg-white/10 text-white flex items-center justify-center hover:bg-white/20 transition-colors"
            >
              ✕
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
const props = defineProps<{
  asset: any
  projectId: number
}>()

const emit = defineEmits<{
  close: []
}>()

const { getMediaFileUrl } = useApi()
const { token: authToken } = useApi()
const imageUrl = ref(props.asset?.url || '')
const videoUrl = ref('')

// 组件挂载时加载带 token 的资源 URL
onMounted(async () => {
  if (!props.asset?.id) return

  if (props.asset.mediaType === 'video') {
    // 视频使用落地文件 URL + token 参数（<video> 标签无法设 Authorization header）
    const base = useRuntimeConfig().public.apiBase || '/api/v1'
    const t = authToken.value || ''
    videoUrl.value = `${base}/media/${props.asset.id}/file${t ? '?token=' + t : ''}`
  } else {
    // 图片走 blob URL 缓存
    imageUrl.value = await getMediaFileUrl(props.asset)
  }
})

const linkToProject = () => {
  // TODO: 打开关联选择浮层
  console.log('link to project')
}
</script>