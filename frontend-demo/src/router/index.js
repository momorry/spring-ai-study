import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 页签标题后缀，需与 index.html 里的初始 <title> 保持一致，避免首屏先闪一下旧文案
const TITLE_SUFFIX = '青梧小筑'

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
          component: () => import('@/views/AgentChat.vue'),
          meta: { title: '', requiresLayout: true }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

// 路由守卫：只负责鉴权跳转，不再顺手改标题（原因见下方 afterEach）
router.beforeEach((to, from, next) => {
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

/**
 * 页签标题只在导航真正落地后设置。
 * <p>写在 beforeEach 里不行：那里面对的是「尚未确认的导航」，一旦随后被守卫取消、或目标与当前路由
 * 相同被 vue-router 判为重复导航（源码里重复导航会直接跳过 navigate， beforeEach 根本不跑），
 * 标题已经改过却没人改回来 → 页面停在对话页、页签却一直是「登录」。
 * <p>也不能无条件写在 afterEach 里：aborted / duplicated 同样会触发 afterEach，
 * 此时 to 是那个没能落地的目标（已核对 vue-router 4.6.4 源码 triggerAfterEach 调用点），
 * 必须靠第三个参数 failure 过滤掉。
 */
router.afterEach((to, from, failure) => {
  if (failure) return // 被取消/中止/重复的导航不改标题
  document.title = to.meta.title ? `${to.meta.title} - ${TITLE_SUFFIX}` : TITLE_SUFFIX
})

export default router
