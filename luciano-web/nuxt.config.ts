// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2026-06-01',

  devtools: { enabled: false },

  modules: ['@nuxtjs/tailwindcss'],

  app: {
    head: {
      title: 'Luciano',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'AI 视频创作平台' },
      ],
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
      ],
    },
  },

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8090/api/v1',
    },
  },

  // 全局路由中间件
  routeRules: {
    '/login': { ssr: false },
  },
})