import router from '@/router'
import { useUserStore } from '@/stores/user'

/**
 * 登录态管理：axios 拦截器与原生 fetch（SSE 流式接口不走 axios）两条通道共用同一套清理与跳转逻辑，
 * 避免行为分叉（历史上 fetch 通道漏了 401 处理，流式接口过期后不跳登录页）。
 */

/**
 * 清理本地登录状态。
 * <p>必须先复位 pinia store：userStore.isLogin() 读的是内存里的 token ref（只在 store 创建时
 * 从 localStorage 取一次），只删 localStorage 不会改变它的值，
 * 于是路由守卫会认定「仍登录、却在访问 login 页」而把用户弹回首页，跳转看起来失败了。
 */
export const clearLoginState = () => {
  try {
    // reset() 内部会同时清掉 localStorage 的 token 与 userInfo
    useUserStore().reset()
  } catch (e) {
    // 应用启动极早期 pinia 尚未就绪时退回直接清理，不能让 401 处理本身抛错
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }
}

/**
 * 401 统一处理：清理登录态并跳转登录页，带上原地址便于登录后回跳。
 */
export const handleUnauthorized = () => {
  clearLoginState()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
}
