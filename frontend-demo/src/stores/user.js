import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getCurrentUser } from '@/api/auth'

const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'

/**
 * 用户认证 store：管理 JWT token 与登录用户信息
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))

  const setToken = (val) => {
    token.value = val || ''
    if (val) {
      localStorage.setItem(TOKEN_KEY, val)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  const setUserInfo = (info) => {
    userInfo.value = info || null
    if (info) {
      localStorage.setItem(USER_KEY, JSON.stringify(info))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  }

  /**
   * 登录：调用后端接口，保存 token 与用户信息
   */
  async function login(payload) {
    const data = await loginApi(payload)
    setToken(data.token)
    setUserInfo({
      userId: data.userId,
      loginAcct: data.loginAcct,
      nickName: data.nickName
    })
    return data
  }

  /**
   * 拉取当前登录用户信息
   */
  async function fetchCurrentUser() {
    const data = await getCurrentUser()
    if (data) {
      setUserInfo({
        userId: data.userId,
        loginAcct: data.loginAcct,
        nickName: data.nickName
      })
    }
    return data
  }

  /**
   * 登出：通知后端并清理本地状态
   */
  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // 忽略登出接口异常，本地状态仍需清理
    } finally {
      reset()
    }
  }

  /**
   * 清理本地登录状态
   */
  function reset() {
    setToken('')
    setUserInfo(null)
  }

  const isLogin = () => !!token.value

  return {
    token,
    userInfo,
    login,
    logout,
    fetchCurrentUser,
    reset,
    isLogin
  }
})
