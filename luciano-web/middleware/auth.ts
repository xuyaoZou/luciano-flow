/**
 * Auth middleware — 未登录跳转 /login
 */
export default defineNuxtRouteMiddleware((to) => {
  const token = useCookie('luciano-token')
  if (!token.value && to.path !== '/login') {
    return navigateTo('/login')
  }
})