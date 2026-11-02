import request from '@/utils/request'

/**
 * 发送聊天消息（流式）
 * @param {string} endpoint - API 端点路径
 * @param {Object} data - 请求数据
 * @param {string} data.message - 用户消息
 * @param {string} data.sessionId - 会话 ID
 * @param {string} data.userId - 用户 ID
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
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    body: JSON.stringify({
      message: data.message,
      sessionId: data.sessionId || '1',
      userId: data.userId || '1'
    }),
    signal: controller.signal
  })
    .then(async response => {
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
      if (error.name !== 'AbortError') {
        console.error('流式请求错误:', error)
        onEvent({ error: error.message }, false)
      }
    })

  return () => controller.abort()
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
      sessionId: data.sessionId || '1',
      userId: data.userId || '1'
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
    headers: {
      'Content-Type': 'application/json',
    },
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
