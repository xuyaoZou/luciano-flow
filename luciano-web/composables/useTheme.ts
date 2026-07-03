/**
 * 主题切换 composable
 * 默认深色，切换时给 <html> 加/移除 .light class
 * 持久化到 localStorage
 * 使用模块级单例，确保所有组件共享同一个响应式状态
 */

// 模块级单例 —— 所有 useTheme() 调用共享同一个 ref
const theme = ref<'dark' | 'light'>('dark')
const initialized = ref(false)

export const useTheme = () => {
  // 只初始化一次
  if (!initialized.value) {
    onMounted(() => {
      const saved = localStorage.getItem('theme') as 'dark' | 'light' | null
      if (saved) {
        theme.value = saved
      } else if (window.matchMedia('(prefers-color-scheme: light)').matches) {
        theme.value = 'light'
      }
      applyTheme()
      initialized.value = true
    })
  }

  const applyTheme = () => {
    if (import.meta.client) {
      document.documentElement.classList.toggle('light', theme.value === 'light')
    }
  }

  const toggleTheme = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem('theme', theme.value)
    applyTheme()
  }

  const isDark = computed(() => theme.value === 'dark')

  return { theme, isDark, toggleTheme }
}