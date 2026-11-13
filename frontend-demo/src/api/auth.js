import request from '@/utils/request'

/**
 * 注册（登录号仅支持手机号 / 邮箱）
 * @param {Object} data - { loginAcct, password, nickName }
 * @returns {Promise} 后端返回的 LoginResultVo，注册成功后自动登录
 */
export function register(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

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
