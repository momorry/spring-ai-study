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
          <el-avatar :size="40" :style="{ backgroundColor: message.role === 'user' ? '#E85D04' : '#67c23a' }">
            {{ message.role === 'user' ? '我' : '青梧' }}
          </el-avatar>
        </div>
        
        <div class="message-content">
          <div class="message-header">
            <span class="message-role">
              {{ message.role === 'user' ? '我' : '青梧' }}
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
          
          <!-- 文件产物展示 -->
          <div v-if="message.fileInfo" class="file-attachment">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ message.fileInfo.fileName }}</span>
            <span class="file-size" v-if="message.fileInfo.fileSize">({{ formatFileSize(message.fileInfo.fileSize) }})</span>
            <el-button type="primary" link @click="handleDownloadFile(message)">
              <el-icon><Download /></el-icon>
              下载
            </el-button>
          </div>
          
          <!-- 消息操作按钮 -->
          <div class="message-actions" v-if="message.role === 'assistant'">
            <el-button size="small" text @click="handleCopy(getFullContent(message))">
              <el-icon><DocumentCopy /></el-icon>
              复制
            </el-button>
            <el-button v-if="index > 0" size="small" text @click="handleRegenerate(index)">
              <el-icon><RefreshRight /></el-icon>
              重新生成
            </el-button>
          </div>
        </div>
      </div>
      
      <!-- 加载指示器 -->
      <div v-if="loading" class="message-item assistant">
        <div class="message-avatar">
          <el-avatar :size="40" style="background-color: #67c23a">青梧</el-avatar>
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
          <el-button
            type="success"
            plain
            @click="handleNewConversation"
            :disabled="loading"
            title="新建会话"
          >
            <el-icon><Plus /></el-icon>
            新建会话
          </el-button>
        </div>
        
        <div class="right-actions">
          <el-button
            v-if="loading"
            type="danger"
            :icon="VideoPause"
            @click="handleStop"
          >
            停止生成
          </el-button>
          <el-button
            v-else
            type="primary"
            :icon="Promotion"
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
import MarkdownIt from 'markdown-it'
import {
  Paperclip,
  Picture,
  Promotion,
  DocumentCopy,
  RefreshRight,
  ChatDotRound,
  VideoPause,
  Document,
  Download,
  Plus
} from '@element-plus/icons-vue'

// 初始化 markdown-it 实例
const md = new MarkdownIt({
  html: false,        // 不解析原始 HTML（安全）
  breaks: true,       // 将 \n 转换为 <br>
  linkify: true,      // 自动将 URL 转为链接
  typographer: false
})

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

const emit = defineEmits(['send', 'copy', 'regenerate', 'stop', 'download-file', 'new-conversation'])

const inputMessage = ref('')
const messageListRef = ref(null)

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + units[i]
}

// 下载文件
const handleDownloadFile = (message) => {
  // 触发下载事件，由父组件处理具体逻辑，传递完整的 message 对象
  emit('download-file', message)
}

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

// 格式化消息内容（使用 markdown-it 渲染）
const formatMessage = (content) => {
  if (!content) return ''
  return md.render(content)
}

// 新建会话
const handleNewConversation = () => {
  emit('new-conversation')
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

// 停止生成
const handleStop = () => {
  emit('stop')
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
  background: #F9E9CD;
  border-radius: 16px;
  overflow: hidden;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #F9E9CD;
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
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
}

.message-item.user .message-content {
  background: linear-gradient(135deg, #F48C06, #E85D04);
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

/* 代码块样式（markdown-it 输出 <pre><code>） */
.message-text :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0;
  font-size: 13px;
  line-height: 1.5;
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
  font-size: inherit;
  border-radius: 0;
}

/* 行内代码样式 */
.message-text :deep(:not(pre) > code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 0.9em;
  color: #e83e8c;
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3) {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.message-text :deep(h1) { font-size: 1.4em; }
.message-text :deep(h2) { font-size: 1.2em; }
.message-text :deep(h3) { font-size: 1.1em; }

.message-text :deep(ul) {
  padding-left: 24px;
  margin: 8px 0;
}

.message-text :deep(li) {
  margin: 6px 0;
  line-height: 1.7;
}

.message-text :deep(a) {
  color: #E85D04;
  text-decoration: none;
}

.message-text :deep(a:hover) {
  text-decoration: underline;
}

.message-text :deep(hr) {
  border: none;
  border-top: 1px solid #e4e7ed;
  margin: 12px 0;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.message-text :deep(em) {
  font-style: italic;
}

/* Markdown 表格样式（markdown-it 输出标准 <table>） */
.message-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 14px;
}

.message-text :deep(table th),
.message-text :deep(table td) {
  border: 1px solid #e4e7ed;
  padding: 8px 12px;
  text-align: left;
}

.message-text :deep(table th) {
  background: #f5f7fa;
  font-weight: 600;
  color: #303133;
}

.message-text :deep(table tr:nth-child(even)) {
  background: #fafafa;
}

.message-text :deep(table tr:hover) {
  background: #f0f9ff;
}

/* 引用块样式 */
.message-text :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 16px;
  border-left: 4px solid #E85D04;
  background: #fafafa;
  color: #606266;
}

/* 有序列表样式 */
.message-text :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}

/* 文件产物样式 */
.file-attachment {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 14px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 6px;
  font-size: 13px;
}

.file-attachment .el-icon {
  color: #0284c7;
  font-size: 16px;
}

.file-attachment .file-name {
  color: #0369a1;
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-attachment .file-size {
  color: #64748b;
  font-size: 12px;
}

.file-attachment .el-button {
  margin-left: auto;
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
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  padding: 16px 20px;
  background: #F9E9CD;
}

/* 输入框圆角 */
.input-area :deep(.el-textarea__inner) {
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.input-area :deep(.el-textarea__inner:focus) {
  border-color: #F5B199;
}

/* 按钮圆角 */
.input-area :deep(.el-button) {
  border-radius: 16px;
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
  align-items: center;
}

/* 滚动条样式 */
.message-list::-webkit-scrollbar {
  width: 6px;
}

.message-list::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
}

.message-list::-webkit-scrollbar-thumb {
  background: rgba(232, 93, 4, 0.3);
  border-radius: 3px;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: rgba(232, 93, 4, 0.5);
}

/* 响应式 */
@media (max-width: 768px) {
  .message-content {
    max-width: 85%;
  }
}
</style>
