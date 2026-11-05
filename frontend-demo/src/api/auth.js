import request from '@/utils/request'

/**
 * 登录
 * @param {Object} data - { loginAcct, password }
 * @returns {Promise} 后端返回的 LoginResultVo（token、expiresIn、userId、loginAcct、nickName）
 */
export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

/**
 * 登出
 */
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

/**
 * 获取当前登录用户信息
 */
export function getCurrentUser() {
  return request({
    url: '/auth/currentUser',
    method: 'get'
  })
}
