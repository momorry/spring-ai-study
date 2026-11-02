import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue')
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('@/views/AboutView.vue')
    },
    // 聊天页面路由
    {
      path: '/chat/react-agent',
      name: 'react-agent-chat',
      component: () => import('@/views/ReactAgentChat.vue'),
      meta: { title: 'ReAct Agent 对话', requiresLayout: true }
    },
    {
      path: '/chat/harness-agent',
      name: 'harness-agent-chat',
      component: () => import('@/views/HarnessAgentChat.vue'),
      meta: { title: 'Harness Agent 对话', requiresLayout: true }
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - AI Chat Demo`
  }
  next()
})

export default router
