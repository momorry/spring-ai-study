import axios from 'axios'
import router from '@/router'

// 401 统一处理：清理登录态并跳转登录页
const handleUnauthorized = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
}

// 创建 axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 在发送请求之前做些什么
    // 例如：添加 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    // 对请求错误做些什么
    console.error('请求错误：', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    // 对响应数据做点什么
    const res = response.data
    
    // 假设后端返回的数据格式为 { code: 200, data: {}, message: '' }
    if (res.code === 200) {
      return res.data
    } else if (res.code === 401) {
      // 未登录或登录过期
      handleUnauthorized()
      return Promise.reject(new Error(res.message || '未登录'))
    } else {
      // 处理错误
      console.error('响应错误：', res.message)
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  (error) => {
    // HTTP 401 也需跳转登录
    if (error.response && error.response.status === 401) {
      handleUnauthorized()
    }
    // 对响应错误做点什么
    console.error('响应错误：', error)
    return Promise.reject(error)
  }
)

export default service
