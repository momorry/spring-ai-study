import request from '@/utils/request'

/**
 * 分页查询当前登录用户的历史会话列表（置顶优先，其次按更新时间倒序）
 * @param {Object} params - { pageNum, pageSize }
 * @returns {Promise} 后端返回的 MyBatis-Plus Page 对象：
 *   { records: [], total, size, current, pages }
 */
export function pageChatSessions(params) {
  return request({
    url: '/mChatSession/page',
    method: 'get',
    params
  })
}

/**
 * 重命名会话标题
 * @param {number} id - 会话主键
 * @param {string} title - 新标题
 */
export function renameChatSession(id, title) {
  return request({
    url: '/mChatSession/rename',
    method: 'post',
    data: { id, title }
  })
}

/**
 * 删除会话（逻辑删除）
 * @param {number} id - 会话主键
 */
export function deleteChatSession(id) {
  return request({
    url: '/mChatSession/delete',
    method: 'post',
    data: { id }
  })
}
