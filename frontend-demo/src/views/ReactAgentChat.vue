<template>
  <div class="react-agent-page">
    <ChatBox
      ref="chatBoxRef"
      :messages="messages"
      :loading="loading"
      @send="handleSend"
      @copy="handleCopy"
      @regenerate="handleRegenerate"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import ChatBox from '@/components/ChatBox.vue'
import { sendChatMessageStream } from '@/api/chat'

const chatBoxRef = ref(null)
const loading = ref(false)

// 消息列表
const messages = ref([
  {
    role: 'assistant',
    content: '你好！我是 ReAct Agent，一个基于推理和行动的智能助手。我可以帮你解答问题、执行任务。请问有什么可以帮助你的吗？',
    thinking: null,
    timestamp: Date.now()
  }
])

// 解析 AgentScope 2 事件
const parseAgentEvent = (event) => {
  if (!event || !event.type) return { type: null, thinking: null, text: null }
  
  const eventType = event.type
  
  // THINKING_BLOCK_DELTA - 推理阶段增量
  if (eventType === 'THINKING_BLOCK_DELTA') {
    const thinking = event.delta || ''
    return { type: 'thinking', thinking, text: null }
  }
  
  // TEXT_BLOCK_DELTA - 文本阶段增量
  if (eventType === 'TEXT_BLOCK_DELTA') {
    const text = event.delta || ''
    return { type: 'text', thinking: null, text }
  }
  
  // AGENT_RESULT - 最终完整结果
  if (eventType === 'AGENT_RESULT') {
    const content = event.result?.content || []
    let thinking = ''
    let text = ''
    
    content.forEach(item => {
      if (item.type === 'thinking') {
        thinking = item.thinking || ''
      } else if (item.type === 'text') {
        text = item.text || ''
      }
    })
    
    return { type: 'result', thinking, text }
  }
  
  // 其他事件类型（忽略）
  return { type: 'other', thinking: null, text: null }
}

// 发送消息
const handleSend = async (content) => {
  // 添加用户消息
  const userMessage = {
    role: 'user',
    content: content,
    thinking: null,
    timestamp: Date.now()
  }
  messages.value.push(userMessage)
  
  // 添加空的 AI 消息占位
  const aiMessageIndex = messages.value.length
  const aiMessage = {
    role: 'assistant',
    content: '',
    thinking: '',
    timestamp: Date.now()
  }
  messages.value.push(aiMessage)
  
  // 开始加载
  loading.value = true
  
  let thinkingContent = ''
  let textContent = ''
  
  try {
    // 使用流式请求
    sendChatMessageStream('/api/reactagent/conversation', {
      message: content,
      sessionId: '1',
      userId: '1'
    }, (event, isDone) => {
      if (isDone) {
        // 流完成
        loading.value = false
        if (chatBoxRef.value) {
          chatBoxRef.value.scrollToBottom()
        }
        return
      }
      
      if (event.error) {
        ElMessage.error(`请求失败: ${event.error}`)
        loading.value = false
        return
      }
      
      // 解析事件
      const parsed = parseAgentEvent(event)
      
      // 处理增量更新（流式阶段）
      if (parsed.type === 'thinking' && parsed.thinking) {
        thinkingContent += parsed.thinking
        messages.value[aiMessageIndex].thinking = thinkingContent
      } else if (parsed.type === 'text' && parsed.text) {
        textContent += parsed.text
        messages.value[aiMessageIndex].content = textContent
      }
      // 处理最终结果（覆盖之前的增量）
      else if (parsed.type === 'result') {
        if (parsed.thinking) {
          thinkingContent = parsed.thinking
          messages.value[aiMessageIndex].thinking = thinkingContent
        }
        if (parsed.text) {
          textContent = parsed.text
          messages.value[aiMessageIndex].content = textContent
        }
      }
      
      // 实时滚动
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

// 复制消息
const handleCopy = (content) => {
  console.log('复制内容:', content)
}

// 重新生成
const handleRegenerate = async (index) => {
  if (index <= 0) return
  
  // 获取上一条用户消息
  const userMessage = messages.value[index - 1]
  if (userMessage.role !== 'user') {
    ElMessage.warning('无法重新生成此消息')
    return
  }
  
  // 删除当前的 AI 回复
  messages.value.splice(index, 1)
  
  // 添加新的空 AI 消息占位
  const aiMessageIndex = messages.value.length
  const aiMessage = {
    role: 'assistant',
    content: '',
    thinking: '',
    timestamp: Date.now()
  }
  messages.value.push(aiMessage)
  
  // 重新发送请求
  loading.value = true
  
  let thinkingContent = ''
  let textContent = ''
  
  try {
    sendChatMessageStream('/api/reactagent/conversation', {
      message: userMessage.content,
      sessionId: '1',
      userId: '1'
    }, (event, isDone) => {
      if (isDone) {
        loading.value = false
        ElMessage.success('已重新生成回复')
        if (chatBoxRef.value) {
          chatBoxRef.value.scrollToBottom()
        }
        return
      }
      
      if (event.error) {
        ElMessage.error(`请求失败: ${event.error}`)
        loading.value = false
        return
      }
      
      // 解析事件
      const parsed = parseAgentEvent(event)
      
      // 处理增量更新（流式阶段）
      if (parsed.type === 'thinking' && parsed.thinking) {
        thinkingContent += parsed.thinking
        messages.value[aiMessageIndex].thinking = thinkingContent
      } else if (parsed.type === 'text' && parsed.text) {
        textContent += parsed.text
        messages.value[aiMessageIndex].content = textContent
      }
      // 处理最终结果（覆盖之前的增量）
      else if (parsed.type === 'result') {
        if (parsed.thinking) {
          thinkingContent = parsed.thinking
          messages.value[aiMessageIndex].thinking = thinkingContent
        }
        if (parsed.text) {
          textContent = parsed.text
          messages.value[aiMessageIndex].content = textContent
        }
      }
      
      // 实时滚动
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
}
</style>
