<template>
  <div class="chat-container">
    <!-- 消息列表区域 -->
    <div class="message-list" ref="messageListRef">
      <div v-if="messages.length === 0" class="empty-state">
        <el-empty description="开始对话吧！">
          <el-icon :size="60" color="#909399"><ChatDotRound /></el-icon>
        </el-empty>
      </div>
      
      <div
        v-for="(message, index) in messages"
        :key="index"
        :class="['message-item', message.role]"
      >
        <div class="message-avatar">
          <el-avatar :size="40" :style="{ backgroundColor: message.role === 'user' ? '#409eff' : '#67c23a' }">
            {{ message.role === 'user' ? '我' : 'AI' }}
          </el-avatar>
        </div>
        
        <div class="message-content">
          <div class="message-header">
            <span class="message-role">
              {{ message.role === 'user' ? '我' : 'AI 助手' }}
            </span>
            <span class="message-time">{{ formatTime(message.timestamp) }}</span>
          </div>
          
          <!-- 推理阶段（可折叠） -->
          <div v-if="message.thinking" class="thinking-section">
            <el-collapse>
              <el-collapse-item title="💭 推理过程" name="thinking">
                <div class="thinking-content" v-html="formatMessage(message.thinking)"></div>
              </el-collapse-item>
            </el-collapse>
          </div>
          
          <!-- 文本阶段 -->
          <div class="message-text" v-html="formatMessage(message.content)"></div>
          
          <!-- 消息操作按钮 -->
          <div class="message-actions" v-if="message.role === 'assistant'">
            <el-button size="small" text @click="handleCopy(getFullContent(message))">
              <el-icon><DocumentCopy /></el-icon>
              复制
            </el-button>
            <el-button size="small" text @click="handleRegenerate(index)">
              <el-icon><RefreshRight /></el-icon>
              重新生成
            </el-button>
          </div>
        </div>
      </div>
      
      <!-- 加载指示器 -->
      <div v-if="loading" class="message-item assistant">
        <div class="message-avatar">
          <el-avatar :size="40" style="background-color: #67c23a">AI</el-avatar>
        </div>
        <div class="message-content">
          <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :rows="3"
        placeholder="输入消息... (Shift+Enter 换行，Enter 发送)"
        :disabled="loading"
        @keydown.enter.exact.prevent="handleSend"
        resize="none"
      />
      
      <div class="input-actions">
        <div class="left-actions">
          <el-button :icon="Paperclip" circle :disabled="loading" />
          <el-button :icon="Picture" circle :disabled="loading" />
        </div>
        
        <div class="right-actions">
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="loading"
            :disabled="!inputMessage.trim()"
            @click="handleSend"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Paperclip,
  Picture,
  Promotion,
  DocumentCopy,
  RefreshRight,
  ChatDotRound
} from '@element-plus/icons-vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send', 'copy', 'regenerate'])

const inputMessage = ref('')
const messageListRef = ref(null)

// 获取完整内容（包括推理过程）
const getFullContent = (message) => {
  if (message.thinking) {
    return `${message.thinking}\n\n---\n\n${message.content}`
  }
  return message.content
}

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

// 格式化消息内容（支持简单的 Markdown）
const formatMessage = (content) => {
  if (!content) return ''
  
  // 简单的换行处理
  return content
    .replace(/\n/g, '<br>')
    .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

// 发送消息
const handleSend = () => {
  const message = inputMessage.value.trim()
  if (!message) return
  
  emit('send', message)
  inputMessage.value = ''
  
  // 滚动到底部
  nextTick(() => {
    scrollToBottom()
  })
}

// 复制消息
const handleCopy = async (content) => {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
    emit('copy', content)
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

// 重新生成
const handleRegenerate = (index) => {
  emit('regenerate', index)
}

// 滚动到底部
const scrollToBottom = () => {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

// 监听消息变化，自动滚动
onMounted(() => {
  scrollToBottom()
})

// 暴露方法给父组件
defineExpose({
  scrollToBottom
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 70%;
  background: #fff;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.message-item.user .message-content {
  background: #409eff;
  color: #fff;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  opacity: 0.7;
}

.message-role {
  font-weight: 500;
}

/* 推理阶段样式 */
.thinking-section {
  margin-bottom: 12px;
}

.thinking-section :deep(.el-collapse) {
  border: none;
}

.thinking-section :deep(.el-collapse-item__header) {
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #909399;
  height: auto;
  line-height: 1.5;
  border: none;
}

.thinking-section :deep(.el-collapse-item__wrap) {
  background: transparent;
  border: none;
}

.thinking-section :deep(.el-collapse-item__content) {
  padding: 8px 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.thinking-content {
  white-space: pre-wrap;
  word-wrap: break-word;
}

.thinking-content :deep(pre) {
  background: rgba(0, 0, 0, 0.03);
  padding: 8px;
  border-radius: 4px;
  font-size: 11px;
  margin: 4px 0;
}

.thinking-content :deep(code) {
  background: rgba(0, 0, 0, 0.03);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11px;
}

.message-text {
  line-height: 1.6;
  word-wrap: break-word;
}

.message-text :deep(pre) {
  background: rgba(0, 0, 0, 0.05);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-text :deep(code) {
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}

.message-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

/* 加载动画 */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #909399;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-10px);
  }
}

/* 输入区域 */
.input-area {
  border-top: 1px solid #e4e7ed;
  padding: 16px 20px;
  background: #fff;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.left-actions,
.right-actions {
  display: flex;
  gap: 8px;
}

/* 滚动条样式 */
.message-list::-webkit-scrollbar {
  width: 6px;
}

.message-list::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.message-list::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 响应式 */
@media (max-width: 768px) {
  .message-content {
    max-width: 85%;
  }
}
</style>
