<template>
  <div class="react-agent-page">
    <ChatBox
      ref="chatBoxRef"
      :messages="messages"
      :loading="loading"
      :models="models"
      :current-model="currentModel"
      :models-loading="modelsLoading"
      @send="handleSend"
      @copy="handleCopy"
      @regenerate="handleRegenerate"
      @stop="handleStop"
      @new-conversation="handleNewConversation"
      @download-file="handleDownloadFile"
      @change-model="handleModelChange"
      @confirm="handleConfirm"
    />
  </div>
</template>

<script setup>
import { ref, watch, inject, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChatBox from '@/components/ChatBox.vue'
import { sendChatMessageStream, stopConversation, loadConversationHistory, downloadArtifact } from '@/api/chat'
import { listLlmModels } from '@/api/llmModel'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const chatBoxRef = ref(null)
const loading = ref(false)
const abortFn = ref(null)

// 模型切换：候选列表来自服务端配置表，选中项随对话请求下发
const MODEL_STORAGE_KEY = 'chat-model-name'
const models = ref([])
const modelsLoading = ref(false)
const currentModel = ref(localStorage.getItem(MODEL_STORAGE_KEY) || '')

// 加载可选模型列表，并校准当前选中项（本地记忆失效时回退默认模型）
async function loadModels() {
  modelsLoading.value = true
  try {
    const res = await listLlmModels()
    models.value = Array.isArray(res) ? res : []
    const exists = models.value.some(m => m.modelName === currentModel.value)
    if (!exists) {
      const defaultModel = models.value.find(m => m.isDefault) || models.value[0]
      currentModel.value = defaultModel ? defaultModel.modelName : ''
      if (currentModel.value) {
        localStorage.setItem(MODEL_STORAGE_KEY, currentModel.value)
      }
    }
  } catch (e) {
    // 配置表未初始化或接口异常：不阻断对话，服务端会按默认模型应答
    console.error('加载模型列表失败:', e)
    models.value = []
  } finally {
    modelsLoading.value = false
  }
}

// 切换模型（下一条消息起生效）
function handleModelChange(modelName) {
  currentModel.value = modelName || ''
  if (currentModel.value) {
    localStorage.setItem(MODEL_STORAGE_KEY, currentModel.value)
  } else {
    localStorage.removeItem(MODEL_STORAGE_KEY)
  }
}

onMounted(loadModels)

// 从父组件注入当前会话ID（由后端流式响应生成，前端不主动生成）
const currentSessionId = inject('currentSessionId', ref(null))

// 会话 ID：初始为 null，由后端流式响应返回后赋值；从历史面板选择时也会被设置
const sessionId = ref(null)

// 消息列表（初始为欢迎消息，watcher 会从后端加载历史覆盖）
const messages = ref([
  {
    role: 'assistant',
    content: '你好！我是 ReAct Agent，一个基于推理和行动的智能助手。我可以帮你解答问题、执行任务。请问有什么可以帮助你的吗？',
    thinking: null,
    timestamp: Date.now()
  }
])

// 监听路由 query 变化（从历史面板选择会话时触发）
watch(() => route.query.sessionId, (val) => {
  if (val) {
    sessionId.value = val
    currentSessionId.value = val
    lastLoadedSid = val
    loadHistoryFromBackend(val)
  }
})

// 监听 inject 的 sessionId 变化（从历史面板选择会话时触发）
// 用 lastLoadedSid 追踪已加载的会话，避免与 sessionId 同步更新导致的竞态
let lastLoadedSid = null
watch(currentSessionId, (newId) => {
  if (!newId) {
    // 会话被删除或清空，重置为新建会话状态
    sessionId.value = null
    lastLoadedSid = null
    resetChat()
    return
  }
  if (newId === lastLoadedSid) return  // 已加载过，跳过
  
  sessionId.value = newId
  lastLoadedSid = newId
  loadHistoryFromBackend(newId)
})

// 从后端加载会话历史
async function loadHistoryFromBackend(sid) {
  try {
    const res = await loadConversationHistory(sid)
    // axios 拦截器已在 code===200 时返回 res.data，此处 res 即为消息数组
    if (Array.isArray(res) && res.length > 0) {
      // 将后端返回的历史消息转换为前端消息格式
      messages.value = res.map(msg => ({
        id: msg.id || null,  // 消息ID
        role: msg.role,
        content: msg.content || '',
        thinking: msg.thinking || null,
        timestamp: msg.timestamp ? new Date(msg.timestamp).getTime() : Date.now(),
        // 本轮交付的产物（一条回答可以有好几个），字段与实时流下发的完全一致，复用同一个渲染分支
        artifacts: Array.isArray(msg.artifacts) && msg.artifacts.length ? msg.artifacts : null,
        // 本轮调用过的工具（后端把 role=TOOL 的消息折叠到这里，不再单独成一条气泡）
        toolCalls: Array.isArray(msg.toolCalls) && msg.toolCalls.length
          ? msg.toolCalls
          : null,
        // 刷新前没处理完的人在回路待确认项：后端从上下文的 ASKING 状态把它补回来，否则会话就卡死了
        confirmRequests: Array.isArray(msg.confirmRequests) && msg.confirmRequests.length
          ? msg.confirmRequests
          : null
      }))
    } else {
      // 没有历史记录，显示欢迎消息
      resetChat()
    }
  } catch (e) {
    console.error('加载历史消息失败:', e)
    resetChat()
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
  // 重置 sessionId，等待后端流式响应返回新的 sessionId
  sessionId.value = null
  currentSessionId.value = null
  lastLoadedSid = null
  // 重置消息
  messages.value = [
    {
      role: 'assistant',
      content: '你好！我是 ReAct Agent，请问有什么可以帮助你的吗？',
      thinking: null,
      timestamp: Date.now()
    }
  ]
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

// 监听消息变化，自动滚动（移除 sessionStorage 缓存，统一使用后端数据）

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
  
  // 产物已交付：一个 FILE 事件可能带好几个文件，整组一起下发
  if (eventType === 'file') {
    return { type: 'file', artifacts: Array.isArray(event.artifacts) ? event.artifacts : [] }
  }

  // 人在回路：工具调用等待确认，本轮已暂停（不处理卡片会话会一直卡在 ASKING）
  if (eventType === 'confirm') {
    return {
      type: 'confirm',
      confirmRequests: Array.isArray(event.confirmRequests) ? event.confirmRequests : []
    }
  }

  // 一次工具调用执行完毕：实时流下也把它折成工具调用痕迹，做到不刷新页面就能看到本轮调了什么
  if (eventType === 'tool') {
    return { type: 'tool', toolCalls: Array.isArray(event.toolCalls) ? event.toolCalls : [] }
  }
  
  // 对话完成（携带本轮用户消息 ID，供后续按 ID 定位重新生成的回滚点）
  if (eventType === 'done') {
    return { type: 'done', userMessageId: event.userMessageId || '' }
  }
  
  // 错误
  if (eventType === 'error') {
    return { type: 'error', message: event.errorMessage || '未知错误' }
  }
  
  return { type: 'other', thinking: null, text: null }
}

// 失败统一收口：复位 loading，并把原因写进当前助手气泡（toast 一闪而过，气泡里的文字才能让用户看清发生了什么）
const handleStreamError = (index, message) => {
  const target = messages.value[index]
  if (target && !target.error) {
    target.error = message
    target.content = `${target.content ? target.content + '\n\n' : ''}⚠️ ${message}`
  }
  loading.value = false
  ElMessage.error(message)
  // AI 回答出错，通知刷新会话列表（带上当前会话 id，供列表侧判断是否已存在）
  window.dispatchEvent(new CustomEvent('refresh-session-list', { detail: { sessionId: sessionId.value } }))
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollToBottom()
  }
}

// 记下本轮用户消息 ID：服务端上下文里的这条用户消息就是这个 id，
// 点重新生成时要原样带回，服务端据此精确定位回滚点（文本可能多条完全相同，不能靠文本找）
const applyTurnUserMessageId = (index, userMessageId) => {
  if (!userMessageId) return
  const assistant = messages.value[index]
  if (!assistant) return
  assistant.userMessageId = userMessageId
  // 重新生成会先删掉旧的那条再追加新的，所以本地 id 要跟着换成最新一个，不能只在缺失时补
  const user = messages.value[index - 1]
  if (user && user.role === 'user') {
    user.id = userMessageId
  }
}

// 把实时流下发的工具调用结果追加到这条气泡上，字段与历史加载的 toolCalls 完全一致，复用同一个渲染分支。
// 按 toolCallId 去重覆盖：同一次调用可能先来一条“执行中”、再来一条最终状态，留最后一条即可
const appendToolCalls = (index, incoming) => {
  const target = messages.value[index]
  if (!target) return
  const merged = Array.isArray(target.toolCalls) ? [...target.toolCalls] : []
  incoming.forEach(item => {
    const at = merged.findIndex(existing => existing.toolCallId === item.toolCallId)
    if (at >= 0) {
      merged[at] = { ...merged[at], ...item }
    } else {
      merged.push(item)
    }
  })
  target.toolCalls = merged
}

// 把实时流下发的产物追加到这条气泡上，与 toolCalls 同一套路：按 artifactId 去重，
// 同一个产物就算被重发一次（恢复流会复用同一条气泡）也不会在上面列两遍
const appendArtifacts = (index, incoming) => {
  const target = messages.value[index]
  if (!target) return
  const merged = Array.isArray(target.artifacts) ? [...target.artifacts] : []
  incoming.forEach(item => {
    const at = merged.findIndex(existing => existing.artifactId === item.artifactId)
    if (at >= 0) {
      merged[at] = { ...merged[at], ...item }
    } else {
      merged.push(item)
    }
  })
  target.artifacts = merged
}

// 统一的流式消费入口：正常发送、重新生成、确认恢复三条路径走的是同一份事件协议，
// 差异只在收尾提示，所以只留一份解析与写入逻辑（否则新加一种事件时很容易只改其中一条路径）。
// request 只写业务字段，公共字段（sessionId/userId/modelName）在此统一补齐；
// index 是要写入的助手气泡下标——确认恢复复用暂停时的那一条气泡，不新开一条。
const streamIntoMessage = (request, index, { onCompleted, errorTip = '发送消息失败，请稍后重试' } = {}) => {
  const initial = messages.value[index]
  // 累积量以气泡现状为起点：恢复流是接着上一段输出继续写的，从空串累加会把已展示的内容顶掉
  let thinkingContent = initial?.thinking || ''
  let textContent = initial?.content || ''
  let sessionIdCaptured = false

  try {
    abortFn.value = sendChatMessageStream('/api/agent/conversation', {
      sessionId: sessionId.value,
      userId: userStore.userInfo?.userId || '',
      modelName: currentModel.value,
      ...request
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
        if (onCompleted) onCompleted()
        if (chatBoxRef.value) {
          chatBoxRef.value.scrollToBottom({ force: false })
        }
        // AI 回答完成，通知刷新会话列表（带上当前会话 id，供列表侧判断是否已存在）
        window.dispatchEvent(new CustomEvent('refresh-session-list', { detail: { sessionId: sessionId.value } }))
        return
      }
      
      if (event.error) {
        // fromServer：后端统一异常响应已给出可读原因，直接展示；网络层错误才加前缀
        handleStreamError(index, event.fromServer ? event.error : `请求失败: ${event.error}`)
        return
      }
      
      const parsed = parseStreamEvent(event)
      
      if (parsed.type === 'thinking' && parsed.thinking) {
        thinkingContent += parsed.thinking
        messages.value[index].thinking = thinkingContent
      } else if (parsed.type === 'text' && parsed.text) {
        textContent += parsed.text
        messages.value[index].content = textContent
      } else if (parsed.type === 'result') {
        if (parsed.thinking) {
          thinkingContent = parsed.thinking
          messages.value[index].thinking = thinkingContent
        }
        if (parsed.text) {
          textContent = parsed.text
          messages.value[index].content = textContent
        }
      } else if (parsed.type === 'file' && parsed.artifacts.length) {
        // 产物已交付：卡片挂到这条回答上（模型被要求静默交付、不会在正文里提它，前端不接就彻底看不见了）
        appendArtifacts(index, parsed.artifacts)
      } else if (parsed.type === 'confirm' && parsed.confirmRequests.length) {
        // 人在回路：Agent 已带着这些工具调用暂停本轮，把待确认项挂在这条气泡上等用户裁决；
        // 不处理的话会话会停在 ASKING，之后发任何新消息都只会拿到一个报错
        messages.value[index].confirmRequests = parsed.confirmRequests
        ElMessage.warning('有操作等待你确认')
      } else if (parsed.type === 'tool' && parsed.toolCalls.length) {
        // 工具调用收尾：与刷新后从历史里读到的是同一份展示，不必等页面重加载
        appendToolCalls(index, parsed.toolCalls)
      } else if (parsed.type === 'error') {
        // 后端流内异常收敛成的 ERROR 事件（如模型额度用完），收到即结束本轮，不必再等连接关闭
        handleStreamError(index, parsed.message)
        return
      } else if (parsed.type === 'done') {
        // 终止事件：即使 SSE 连接关闭有延迟，也先复位 loading
        // （确认恢复那一轮后端不下发 userMessageId，这里会自行跳过，不会顶掉真正的回滚点）
        applyTurnUserMessageId(index, parsed.userMessageId)
        loading.value = false
        return
      }
      
      // 流式追加不强行拽回底部：用户往上翻看历史时，改由悬浮「回到底部」按钮手动恢复
      if (chatBoxRef.value) {
        chatBoxRef.value.scrollToBottom({ force: false })
      }
    })
  } catch (error) {
    console.error(`${errorTip}:`, error)
    ElMessage.error(errorTip)
    loading.value = false
  }
}

// 发送消息
const handleSend = async (content) => {
  messages.value.push({
    role: 'user',
    content: content,
    thinking: null,
    timestamp: Date.now()
  })
  messages.value.push({
    role: 'assistant',
    content: '',
    thinking: '',
    timestamp: Date.now()
  })
  
  loading.value = true
  streamIntoMessage({ message: content }, messages.value.length - 1)
}

// 人在回路：用户对单个待确认工具调用的裁决（允许本次 / 拒绝），提交后把暂停的会话推下去
const handleConfirm = async ({ index, toolCallId, confirmed }) => {
  const message = messages.value[index]
  const target = message?.confirmRequests?.find(item => item.toolCallId === toolCallId)
  // 已裁决过的条目不再接受第二次点击：同一个 ASKING 调用只认一次确认，重复提交会被服务端拒掉
  if (!target || target.decision) {
    return
  }
  if (loading.value) {
    ElMessage.warning('请等待当前回答生成结束')
    return
  }
  
  // 先把结论写回卡片（按钮随即置灰），再发起恢复请求
  target.decision = confirmed ? 'allow' : 'deny'
  loading.value = true
  
  streamIntoMessage({
    // 恢复请求不带 message：服务端不会把这次请求当成新一轮提问写进上下文
    confirmResults: [{
      toolCallId,
      confirmed,
      // 拒绝原因会作为 DENIED 工具结果写回模型上下文，给一句明确的中文比默认英文更可读
      reason: confirmed ? '' : '用户拒绝执行该操作，请不要重试，改用其他方式回答'
    }]
  }, index, { errorTip: '提交确认失败，请稍后重试' })
}

// 停止生成
const handleStop = async () => {
  if (abortFn.value) {
    abortFn.value()
    abortFn.value = null
  }
  try {
    await stopConversation({ sessionId: sessionId.value, userId: userStore.userInfo?.userId || '' })
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

// 下载单个产物：凭 artifactId 走服务端（它会先校验这个产物是不是当前用户的）
const handleDownloadFile = async (artifact) => {
  if (!artifact?.artifactId) {
    ElMessage.error('产物信息不完整，无法下载')
    return
  }
  
  try {
    const blob = await downloadArtifact(artifact.artifactId)
    // 创建临时下载链接
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = artifact.fileName || 'download'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (error) {
    console.error('下载失败:', error)
    // 产物被同名覆盖过、对象被清理过都会走到这里（服务端给 404），提示往回指而不是只说“稍后重试”
    ElMessage.error('下载失败，产物可能已被新文件覆盖或已被清理，请重新生成后再试')
  }
}

// 重新生成
const handleRegenerate = async (index) => {
  if (index <= 0) return
  
  const userMessage = messages.value[index - 1]
  if (userMessage.role !== 'user') {
    ElMessage.warning('无法重新生成此消息')
    return
  }
  
  // 回滚点定位用的目标消息 ID：历史会话里用户消息自带 id，实时对话用 DONE 事件回传的那个
  const regenerateMessageId = userMessage.id || messages.value[index]?.userMessageId || ''

  // 同会话可能还有一轮正在生成：先中止本地流并通知服务端停下。
  // 否则旧轮次会在服务端截断之后继续把消息刷回上下文，重复问题依旧
  if (loading.value) {
    if (abortFn.value) {
      abortFn.value()
      abortFn.value = null
    }
    try {
      await stopConversation({ sessionId: sessionId.value, userId: userStore.userInfo?.userId || '' })
    } catch (e) {
      // 停下失败不阻断重新生成，后端截断遇到版本冲突会退化为直接覆盖写回
      console.error('停止上一轮生成失败:', e)
    }
    loading.value = false
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
  
  streamIntoMessage({
    message: userMessage.content,
    // 告知服务端先把这一轮上下文回滚，再重放这条消息（否则会重复累积）
    regenerate: true,
    // 按消息 ID 精确定位回滚点；服务端定位不到会直接报错中止本次重新生成（不会退回去猜）
    regenerateMessageId
  }, aiMessageIndex, {
    errorTip: '重新生成失败，请稍后重试',
    // 出错时后端同样会以 ERROR 事件正常收尾，此处不能再报“重新生成成功”
    onCompleted: () => {
      if (!messages.value[aiMessageIndex]?.error) {
        ElMessage.success('已重新生成回复')
      }
    }
  })
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
