import request from '@/utils/request'

/**
 * 查询服务端配置的可选模型列表（渲染聊天框的模型下拉框）
 * @returns {Promise<Array>} [{ modelName, displayName, vendorCode, vendorName, isDefault }]
 */
export function listLlmModels() {
  return request({
    url: '/llm/models',
    method: 'get'
  })
}
