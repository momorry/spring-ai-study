import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true }
    },
    {
      // 需要登录的主布局，所有业务页面作为其子路由
      path: '/',
      component: () => import('@/views/MainLayout.vue'),
      children: [
        {
          path: '',
          redirect: '/chat/react-agent'
        },
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '首页' }
        },
        {
          path: 'about',
          name: 'about',
          component: () => import('@/views/AboutView.vue'),
          meta: { title: '关于' }
        },
        {
          path: 'chat/react-agent',
          name: 'react-agent-chat',
          component: () => import('@/views/ReactAgentChat.vue'),
          meta: { title: 'ReAct Agent 对话', requiresLayout: true }
        },
        {
          path: 'chat/harness-agent',
          name: 'harness-agent-chat',
          component: () => import('@/views/HarnessAgentChat.vue'),
          meta: { title: 'Harness Agent 对话', requiresLayout: true }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

// 路由守卫：所有页面登录后才能访问
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - AI Chat Demo`
  }

  const userStore = useUserStore()

  // 公开页面（登录页）：已登录则直接回首页
  if (to.meta.public) {
    if (userStore.isLogin() && to.name === 'login') {
      return next({ path: '/' })
    }
    return next()
  }

  // 需要鉴权的页面：未登录跳转登录页
  if (!userStore.isLogin()) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }

  next()
})

export default router
