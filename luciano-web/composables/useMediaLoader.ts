/**
 * 媒体图片 blob URL 管理器
 * 解决 <img src> 不能带 Authorization header 的问题
 * 通过 fetch + blob URL 方式带 token 请求图片
 */
export const useMediaLoader = () => {
  const { getMediaFileUrl, releaseMediaBlobs } = useApi()

  // 缓存：asset.id -> blob URL
  const blobUrls = ref<Record<number, string>>({})

  /**
   * 加载单个媒体资源，返回 blob URL
   */
  const loadMedia = async (asset: any): Promise<string> => {
    if (!asset?.id) return asset?.url || ''
    if (blobUrls.value[asset.id]) return blobUrls.value[asset.id]

    const url = await getMediaFileUrl(asset)
    if (url && asset.id) {
      blobUrls.value[asset.id] = url
    }
    return url || asset?.url || ''
  }

  /**
   * 批量加载媒体资源
   */
  const loadMediaList = async (assets: any[]) => {
    await Promise.all(assets.map(a => loadMedia(a)))
  }

  /**
   * 获取已缓存的 URL（同步）
   */
  const getBlobUrl = (assetId: number, fallbackUrl?: string): string => {
    return blobUrls.value[assetId] || fallbackUrl || ''
  }

  /**
   * 清理缓存
   */
  const cleanup = () => {
    releaseMediaBlobs()
    blobUrls.value = {}
  }

  return {
    blobUrls,
    loadMedia,
    loadMediaList,
    getBlobUrl,
    cleanup,
  }
}