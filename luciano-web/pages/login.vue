<!-- 登录页 — 极简，一入即用 -->
<template>
  <div class="min-h-screen flex items-center justify-center px-6 bg-luciano-bg">
    <div class="w-full max-w-xs animate-fade-in">
      <!-- Logo -->
      <div class="text-center mb-8">
        <div class="text-4xl mb-3">⚡</div>
        <h1 class="text-2xl font-bold tracking-tight">Luciano</h1>
        <p class="text-xs text-luciano-muted mt-1">AI 视频创作平台</p>
      </div>

      <!-- 登录/注册切换 -->
      <div class="flex mb-6 bg-luciano-card rounded-xl p-1">
        <button
          @click="mode = 'login'"
          class="flex-1 py-2 text-xs font-medium rounded-lg transition-all"
          :class="mode === 'login' ? 'bg-luciano-bg text-luciano-text' : 'text-luciano-muted'"
        >
          登录
        </button>
        <button
          @click="mode = 'register'"
          class="flex-1 py-2 text-xs font-medium rounded-lg transition-all"
          :class="mode === 'register' ? 'bg-luciano-bg text-luciano-text' : 'text-luciano-muted'"
        >
          注册
        </button>
      </div>

      <!-- 表单 -->
      <form @submit.prevent="handleSubmit" class="space-y-3">
        <input
          v-model="form.username"
          type="text"
          placeholder="用户名"
          autocomplete="username"
          class="w-full bg-luciano-card border border-luciano-border/50 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
        />
        <input
          v-if="mode === 'register'"
          v-model="form.email"
          type="email"
          placeholder="邮箱（选填）"
          class="w-full bg-luciano-card border border-luciano-border/50 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
        />
        <input
          v-model="form.password"
          type="password"
          placeholder="密码"
          autocomplete="current-password"
          class="w-full bg-luciano-card border border-luciano-border/50 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-apple-blue/50 transition-colors"
        />
        <button
          type="submit"
          :disabled="!form.username || !form.password || loading"
          class="w-full py-3 bg-apple-blue text-white text-sm font-medium rounded-xl disabled:opacity-30 hover:bg-apple-blue/90 active:scale-[0.98] transition-all"
        >
          {{ loading ? '处理中…' : (mode === 'login' ? '登录' : '注册') }}
        </button>
      </form>

      <!-- 错误 -->
      <p v-if="error" class="text-xs text-apple-red text-center mt-3">{{ error }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ layout: 'blank' })

const { login, register } = useApi()
const { fetchUser } = useAuth()

const mode = ref<'login' | 'register'>('login')
const form = reactive({ username: '', password: '', email: '' })
const loading = ref(false)
const error = ref('')

// 已登录则跳转首页
const tokenCookie = useCookie('luciano-token')
if (tokenCookie.value) {
  navigateTo('/')
}

const handleSubmit = async () => {
  loading.value = true
  error.value = ''
  try {
    if (mode.value === 'login') {
      await login(form.username, form.password)
    } else {
      await register(form.username, form.password, form.email || undefined)
    }
    await fetchUser()
    navigateTo('/')
  } catch (e: any) {
    // $fetch error 格式
    const msg = e?.data?.message || e?.message || (mode.value === 'login' ? '登录失败' : '注册失败')
    error.value = msg
  } finally {
    loading.value = false
  }
}
</script>