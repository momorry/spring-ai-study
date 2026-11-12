import request from '@/utils/request'
// 本文件的流式对话用原生 fetch，不经过 axios 响应拦截器，401 需在此通道的每个非 2xx 分支里显式处理
import { handleUnauthorized } from '@/utils/session'

// 构造带 JWT token 的请求头（原生 fetch 不走 axios 拦截器）
function authHeaders(extra = {}) {
  const token = localStorage.getItem('token')
  return {
    ...extra,
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  }
}

/**
 * 解析非 2xx 响应体，拿到业务码与可读错误原因。
 * <p>后端 GlobalExceptionHandler / LoginInterceptor 统一返回 ApiResult{success, code, message}，
 * 优先取它的 code 与 message；取不到（网关的 HTML 错误页、纯文本等）则退回状态码提示，
 * 避免把整段 HTML 塞进气泡。code 用于识别 401（网关有时会只透传 body 而丢掉状态码）。
 * @returns {{ code: number, message: string }}
 */
function parseErrorPayload(text, status) {
  const raw = (text || '').trim()
  if (!raw) return { code: status, message: `服务异常（HTTP ${status}）` }
  if (raw.startsWith('<')) return { code: status, message: `服务异常（HTTP ${status}），请查看服务端日志` }
  try {
    const body = JSON.parse(raw)
    if (body && typeof body === 'object') {
      const message = typeof body.message === 'string' && body.message.trim()
        ? body.message
        : `服务异常（HTTP ${status}）`
      return { code: Number(body.code) || status, message }
    }
  } catch (e) {
    // 不是 JSON，原文截断展示
  }
  return { code: status, message: raw.slice(0, 200) }
}

/**
 * 发送聊天消息（流式）
 * @param {string} endpoint - API 端点路径
 * @param {Object} data - 请求数据
 * @param {string} data.message - 用户消息
 * @param {string} data.sessionId - 会话 ID
 * @param {string} data.userId - 用户 ID
 * @param {string} [data.modelName] - 本次对话使用的模型标识（下拉选择值）；为空则由服务端使用默认模型
 * @param {boolean} [data.regenerate] - 是否为重新生成：服务端会先把该轮上下文回滚再重放本条消息
 * @param {string} [data.regenerateMessageId] - 要重放的那条用户消息 ID，服务端按它精确定位回滚点
 * @param {Function} onEvent - 事件回调函数
 * @returns {Function} 取消函数
 */
export function sendChatMessageStream(endpoint, data, onEvent) {
  const controller = new AbortController()

  // 单条 payload 解析并派发（兼容对象与数组）
  let dispatched = false
  let doneEmitted = false
  const emitPayload = (payload) => {
    const text = (payload || '').trim()
    if (!text) return
    if (text === '[DONE]') {
      if (!doneEmitted) {
        doneEmitted = true
        onEvent(null, true)
      }
      return
    }
    try {
      const parsed = JSON.parse(text)
      dispatched = true
      if (Array.isArray(parsed)) {
        parsed.forEach(e => onEvent(e, false))
      } else {
        onEvent(parsed, false)
      }
    } catch (e) {
      // 单行不是完整 JSON（例如聚合数组被拆行），忽略，最后统一兜底解析
    }
  }

  fetch(endpoint, {
    method: 'POST',
    headers: authHeaders({
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    }),
    body: JSON.stringify({
      message: data.message,
      sessionId: data.sessionId || '',
      userId: data.userId || '',
      // 服务端按此标识动态切换本次对话调用的模型（见 DynamicModelMiddleware）
      modelName: data.modelName || '',
      // 重新生成标志：服务端据此回滚上下文，避免同一条消息在上下文里重复累积
      regenerate: !!data.regenerate,
      // 定位回滚点的目标消息 ID（历史消息自带、实时对话由 DONE 事件回传）
      regenerateMessageId: data.regenerateMessageId || ''
    }),
    signal: controller.signal
  })
    .then(async response => {
      // 非 2xx：SSE 尚未建立（未登录、网关异常、参数报错、重新生成定位失败等），响应体不是事件流。
      // 必须显式报错收口，否则前端只会读到一个“空流”，界面没有任何反馈
      if (!response.ok) {
        const detail = await response.text().catch(() => '')
        const { code, message } = parseErrorPayload(detail, response.status)
        // 401 单独处理：不跳转就会停在对话页，且因为 token 已被清空，后续每次重试都只会回
        // “未登录：请求头中缺少 JWT TOKEN”，看起来像“报错不跳登录页”的死循环
        if (response.status === 401 || code === 401) {
          handleUnauthorized()
        }
        // fromServer: 原因已由后端统一给出，前端直接展示，不再叠加“请求失败”前缀
        onEvent({ error: message, fromServer: true }, false)
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let rawAll = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const chunk = decoder.decode(value, { stream: true })
        rawAll += chunk
        buffer += chunk

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed) continue
          // SSE 帧：去掉 data: 前缀；NDJSON：直接是 JSON
          if (trimmed.startsWith('data:')) {
            emitPayload(trimmed.slice(5))
          } else {
            emitPayload(trimmed)
          }
        }
      }

      // 处理缓冲区剩余数据
      if (buffer.trim()) {
        const trimmed = buffer.trim()
        if (trimmed.startsWith('data:')) {
          emitPayload(trimmed.slice(5))
        } else {
          emitPayload(trimmed)
        }
      }

      // 兜底：整段响应是一个聚合 JSON 数组（后端未流式返回时）
      if (!dispatched && rawAll.trim()) {
        emitPayload(rawAll)
      }

      if (!doneEmitted) {
        doneEmitted = true
        onEvent(null, true) // 完成
      }
    })
    .catch(error => {
      // AbortError 是用户主动停止，不需报错；其余（网络中断、流读取异常等）
      // 都必须回调，不然 loading 永远停不下来
      if (error.name !== 'AbortError') {
        console.error('流式请求错误:', error)
        onEvent({ error: error.message }, false)
      }
    })

  return () => controller.abort()
}

/**
 * 停止当前对话生成
 * @param {Object} data - 请求数据
 * @param {string} data.sessionId - 会话 ID
 * @param {number} data.userId - 用户 ID
 * @returns {Promise}
 */
export function stopConversation(data) {
  return request({
    url: '/agent/conversation/stop',
    method: 'post',
    data: {
      sessionId: data.sessionId || '',
      userId: data.userId || null
    }
  })
}

/**
 * 发送聊天消息（非流式，向后兼容）
 * @param {string} endpoint - API 端点路径
 * @param {Object} data - 请求数据
 * @param {string} data.message - 用户消息
 * @param {string} data.sessionId - 会话 ID
 * @param {string} data.userId - 用户 ID
 * @returns {Promise}
 */
export function sendChatMessage(endpoint, data) {
  return request({
    url: endpoint,
    method: 'post',
    data: {
      message: data.message,
      sessionId: data.sessionId || '',
      userId: data.userId || ''
    }
  })
}

/**
 * 获取对话历史
 * @param {string} endpoint - API 端点路径
 * @param {string} sessionId - 会话 ID
 * @returns {Promise}
 */
export function getChatHistory(endpoint, sessionId) {
  return request({
    url: `${endpoint}/history`,
    method: 'get',
    params: { sessionId }
  })
}

/**
 * 加载会话历史消息（从 AgentScope 持久化存储）
 * @param {string} sessionId - 会话 ID
 * @returns {Promise}
 */
export function loadConversationHistory(sessionId) {
  return request({
    url: '/conversation/message/history',
    method: 'get',
    params: { sessionId }
  })
}

/**
 * 清空对话历史
 * @param {string} endpoint - API 端点路径
 * @param {string} sessionId - 会话 ID
 * @returns {Promise}
 */
export function clearChatHistory(endpoint, sessionId) {
  return request({
    url: `${endpoint}/history`,
    method: 'delete',
    params: { sessionId }
  })
}

/**
 * 发送流式聊天消息
 * @param {string} endpoint - API 端点路径
 * @param {Object} data - 请求数据
 * @param {Function} onMessage - 消息回调
 * @param {Function} onError - 错误回调
 * @returns {Function} 取消函数
 */
export function sendStreamChatMessage(endpoint, data, onMessage, onError) {
  const controller = new AbortController()
  
  fetch(endpoint, {
    method: 'POST',
    headers: authHeaders({
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({
      message: data.message,
      history: data.history || [],
      timestamp: Date.now(),
      stream: true
    }),
    signal: controller.signal
  })
    .then(async response => {
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        const chunk = decoder.decode(value, { stream: true })
        const lines = chunk.split('\n')
        
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6)
            if (data === '[DONE]') {
              onMessage(null, true)
              return
            }
            try {
              const parsed = JSON.parse(data)
              onMessage(parsed)
            } catch (e) {
              // 忽略解析错误
            }
          }
        }
      }
    })
    .catch(error => {
      if (error.name !== 'AbortError') {
        onError(error)
      }
    })
  
  return () => controller.abort()
}

// ==================== 文件产物相关 API ====================

/**
 * 根据消息ID下载文件产物
 * @param {string} messageId - 消息ID
 * @returns {Promise<Blob>} 文件 Blob 数据
 */
export function downloadFileByMessageId(messageId) {
  return request({
    url: '/file/download',
    method: 'get',
    params: { messageId },
    responseType: 'blob'  // 重要：指定响应类型为 blob
  })
}

/**
 * 根据消息ID获取文件信息
 * @param {string} messageId - 消息ID
 * @returns {Promise<Object>} 文件信息
 */
export function getFileInfoByMessageId(messageId) {
  return request({
    url: '/file/info',
    method: 'get',
    params: { messageId }
  })
}
