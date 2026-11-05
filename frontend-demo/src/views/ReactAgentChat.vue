<template>
  <div class="react-agent-page">
    <ChatBox
      ref="chatBoxRef"
      :messages="messages"
      :loading="loading"
      @send="handleSend"
      @copy="handleCopy"
      @regenerate="handleRegenerate"
      @stop="handleStop"
      @new-conversation="handleNewConversation"
    />
  </div>
</template>

<script setup>
import { ref, watch, inject } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChatBox from '@/components/ChatBox.vue'
import { sendChatMessageStream, stopConversation, loadConversationHistory } from '@/api/chat'

const route = useRoute()
const chatBoxRef = ref(null)
const loading = ref(false)
const abortFn = ref(null)

// 从父组件注入当前会话ID
const currentSessionId = inject('currentSessionId', ref('1'))

// 会话 ID：优先从路由 query 获取，否则使用 inject 的值
const sessionId = ref(route.query.sessionId || currentSessionId.value)

// 消息列表（初始为欢迎消息，watcher 会从后端加载历史覆盖）
const messages = ref([
  {
    role: 'assistant',
    content: '你好！我是 ReAct Agent，一个基于推理和行动的智能助手。我可以帮你解答问题、执行任务。请问有什么可以帮助你的吗？',
    thinking: null,
    timestamp: Date.now()
  }
])

// 监听路由 query 变化
watch(() => route.query.sessionId, (val) => {
  if (val) {
    sessionId.value = val
    resetChat()
  }
})

// 监听 inject 的 sessionId 变化（从历史面板选择会话时触发）
// 用 lastLoadedSid 追踪已加载的会话，避免与 sessionId 同步更新导致的竞态
let lastLoadedSid = null
watch(currentSessionId, (newId) => {
  if (!newId) return
  if (newId === lastLoadedSid) return  // 已加载过，跳过
  
  sessionId.value = newId
  lastLoadedSid = newId
  loadHistoryFromBackend(newId)
}, { immediate: true })

// 从后端加载会话历史
async function loadHistoryFromBackend(sid) {
  try {
    const res = await loadConversationHistory(sid)
    // axios 拦截器已在 code===200 时返回 res.data，此处 res 即为消息数组
    if (Array.isArray(res) && res.length > 0) {
      // 将后端返回的历史消息转换为前端消息格式
      messages.value = res.map(msg => ({
        role: msg.role,
        content: msg.content || '',
        thinking: msg.thinking || null,
        timestamp: msg.timestamp ? new Date(msg.timestamp).getTime() : Date.now(),
        fileInfo: msg.fileInfo || null
      }))
    } else {
      // 没有历史记录，显示欢迎消息
      resetChat()
    }
  } catch (e) {
    console.error('加载历史消息失败:', e)
    // 加载失败时尝试从 sessionStorage 加载
    const cached = loadMessages()
    if (cached && cached.length > 1) {
      messages.value = cached
    } else {
      resetChat()
    }
  }
}

// 新建会话
function handleNewConversation() {
  // 中止当前流
  if (abortFn.value) {
    abortFn.value()
    abortFn.value = null
  }
  loading.value = false
  // 生成新的会话ID（UUID）
  const newSessionId = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)
  sessionId.value = newSessionId
  currentSessionId.value = newSessionId
  lastLoadedSid = newSessionId  // 标记已加载，避免 watcher 重复请求
  // 重置消息
  messages.value = [
    {
      role: 'assistant',
      content: '你好！我是 ReAct Agent，请问有什么可以帮助你的吗？',
      thinking: null,
      timestamp: Date.now()
    }
  ]
  saveMessages()
  ElMessage.success('已新建会话')
}

// 重置对话
function resetChat() {
  if (abortFn.value) {
    abortFn.value()
    abortFn.value = null
  }
  loading.value = false
  
  messages.value = [
    {
      role: 'assistant',
      content: '你好！我是 ReAct Agent，一个基于推理和行动的智能助手。我可以帮你解答问题、执行任务。请问有什么可以帮助你的吗？',
      thinking: null,
      timestamp: Date.now()
    }
  ]
  
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollToBottom()
  }
}

// ---- sessionStorage 持久化 ----
const STORAGE_KEY_PREFIX = 'chat_messages_'

function storageKey(sid) {
  return STORAGE_KEY_PREFIX + sid
}

function saveMessages() {
  try {
    sessionStorage.setItem(storageKey(sessionId.value), JSON.stringify(messages.value))
  } catch (e) {
    // sessionStorage 满了或不可用，忽略
  }
}

function loadMessages() {
  try {
    const raw = sessionStorage.getItem(storageKey(sessionId.value))
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// 监听消息变化，自动缓存
watch(messages, () => {
  saveMessages()
}, { deep: true })

// 解析后端自定义 ChatStreamEvent（防腐层协议）
const parseStreamEvent = (event) => {
  if (!event || !event.type) return { type: null, thinking: null, text: null }
  
  const eventType = event.type
  
  // 推理阶段增量文本
  if (eventType === 'thinking') {
    return { type: 'thinking', thinking: event.delta || '', text: null }
  }
  
  // 回答阶段增量文本
  if (eventType === 'text') {
    return { type: 'text', thinking: null, text: event.delta || '' }
  }
  
  // 最终聚合结果
  if (eventType === 'result') {
    return { type: 'result', thinking: event.thinking || '', text: event.content || '' }
  }
  
  // 文件产物已生成
  if (eventType === 'file') {
    return { type: 'file', fileInfo: event.fileInfo || null }
  }
  
  // 对话完成
  if (eventType === 'done') {
    return { type: 'done' }
  }
  
  // 错误
  if (eventType === 'error') {
    return { type: 'error', message: event.errorMessage || '未知错误' }
  }
  
  return { type: 'other', thinking: null, text: null }
}

// 发送消息
const handleSend = async (content) => {
  const userMessage = {
    role: 'user',
    content: content,
    thinking: null,
    timestamp: Date.now()
  }
  messages.value.push(userMessage)
  
  const aiMessageIndex = messages.value.length
  const aiMessage = {
    role: 'assistant',
    content: '',
    thinking: '',
    timestamp: Date.now()
  }
  messages.value.push(aiMessage)
  
  loading.value = true
  
  let thinkingContent = ''
  let textContent = ''
  let sessionIdCaptured = false
  
  try {
    abortFn.value = sendChatMessageStream('/api/reactagent/conversation', {
      message: content,
      sessionId: sessionId.value,
      userId: '1'
    }, (event, isDone) => {
      // 从第一个事件中捕获后端返回的 sessionId
      if (!sessionIdCaptured && event && event.sessionId) {
        sessionIdCaptured = true
        const newSid = event.sessionId
        if (newSid !== sessionId.value) {
          sessionId.value = newSid
          currentSessionId.value = newSid
          lastLoadedSid = newSid  // 同步更新，避免 watcher 重复加载历史
        }
      }
      
      if (isDone) {
        loading.value = false
        if (chatBoxRef.value) {
          chatBoxRef.value.scrollToBottom()
        }
        // AI 回答完成，通知刷新会话列表
        window.dispatchEvent(new CustomEvent('refresh-session-list'))
        return
      }
      
      if (event.error) {
        ElMessage.error(`请求失败: ${event.error}`)
        loading.value = false
        // AI 回答出错，通知刷新会话列表
        window.dispatchEvent(new CustomEvent('refresh-session-list'))
        return
      }
      
      const parsed = parseStreamEvent(event)
      
      if (parsed.type === 'thinking' && parsed.thinking) {
        thinkingContent += parsed.thinking
        messages.value[aiMessageIndex].thinking = thinkingContent
      } else if (parsed.type === 'text' && parsed.text) {
        textContent += parsed.text
        messages.value[aiMessageIndex].content = textContent
      } else if (parsed.type === 'result') {
        if (parsed.thinking) {
          thinkingContent = parsed.thinking
          messages.value[aiMessageIndex].thinking = thinkingContent
        }
        if (parsed.text) {
          textContent = parsed.text
          messages.value[aiMessageIndex].content = textContent
        }
      } else if (parsed.type === 'file' && parsed.fileInfo) {
        // 文件产物已生成，附加到消息上
        messages.value[aiMessageIndex].fileInfo = parsed.fileInfo
        console.log('文件产物已生成:', parsed.fileInfo)
      } else if (parsed.type === 'error') {
        ElMessage.error(`请求失败: ${parsed.message}`)
        loading.value = false
        window.dispatchEvent(new CustomEvent('refresh-session-list'))
        return
      }
      
      if (chatBoxRef.value) {
        chatBoxRef.value.scrollToBottom()
      }
    })
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error('发送消息失败，请稍后重试')
    loading.value = false
  }
}

// 停止生成
const handleStop = async () => {
  if (abortFn.value) {
    abortFn.value()
    abortFn.value = null
  }
  try {
    await stopConversation({ sessionId: sessionId.value, userId: 1 })
  } catch (e) {
    console.error('停止请求失败:', e)
  }
  loading.value = false
  ElMessage.info('已停止生成')
}

// 复制消息
const handleCopy = (content) => {
  console.log('复制内容:', content)
}

// 重新生成
const handleRegenerate = async (index) => {
  if (index <= 0) return
  
  const userMessage = messages.value[index - 1]
  if (userMessage.role !== 'user') {
    ElMessage.warning('无法重新生成此消息')
    return
  }
  
  messages.value.splice(index, 1)
  
  const aiMessageIndex = messages.value.length
  const aiMessage = {
    role: 'assistant',
    content: '',
    thinking: '',
    timestamp: Date.now()
  }
  messages.value.push(aiMessage)
  
  loading.value = true
  
  let thinkingContent = ''
  let textContent = ''
  let sessionIdCaptured2 = false
  
  try {
    abortFn.value = sendChatMessageStream('/api/reactagent/conversation', {
      message: userMessage.content,
      sessionId: sessionId.value,
      userId: '1'
    }, (event, isDone) => {
      // 从第一个事件中捕获后端返回的 sessionId
      if (!sessionIdCaptured2 && event && event.sessionId) {
        sessionIdCaptured2 = true
        const newSid = event.sessionId
        if (newSid !== sessionId.value) {
          sessionId.value = newSid
          currentSessionId.value = newSid
          lastLoadedSid = newSid  // 同步更新，避免 watcher 重复加载历史
        }
      }
      
      if (isDone) {
        loading.value = false
        ElMessage.success('已重新生成回复')
        if (chatBoxRef.value) {
          chatBoxRef.value.scrollToBottom()
        }
        window.dispatchEvent(new CustomEvent('refresh-session-list'))
        return
      }
      
      if (event.error) {
        ElMessage.error(`请求失败: ${event.error}`)
        loading.value = false
        window.dispatchEvent(new CustomEvent('refresh-session-list'))
        return
      }
      
      const parsed = parseStreamEvent(event)
      
      if (parsed.type === 'thinking' && parsed.thinking) {
        thinkingContent += parsed.thinking
        messages.value[aiMessageIndex].thinking = thinkingContent
      } else if (parsed.type === 'text' && parsed.text) {
        textContent += parsed.text
        messages.value[aiMessageIndex].content = textContent
      } else if (parsed.type === 'result') {
        if (parsed.thinking) {
          thinkingContent = parsed.thinking
          messages.value[aiMessageIndex].thinking = thinkingContent
        }
        if (parsed.text) {
          textContent = parsed.text
          messages.value[aiMessageIndex].content = textContent
        }
      } else if (parsed.type === 'file' && parsed.fileInfo) {
        // 文件产物已生成，附加到消息上
        messages.value[aiMessageIndex].fileInfo = parsed.fileInfo
        console.log('文件产物已生成:', parsed.fileInfo)
      } else if (parsed.type === 'error') {
        ElMessage.error(`请求失败: ${parsed.message}`)
        loading.value = false
        window.dispatchEvent(new CustomEvent('refresh-session-list'))
        return
      }
      
      if (chatBoxRef.value) {
        chatBoxRef.value.scrollToBottom()
      }
    })
  } catch (error) {
    console.error('重新生成失败:', error)
    ElMessage.error('重新生成失败，请稍后重试')
    loading.value = false
  }
}
</script>

<style scoped>
.react-agent-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: hidden;
}
</style>
