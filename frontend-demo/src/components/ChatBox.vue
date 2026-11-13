<template>
  <div class="chat-container">
    <!-- 消息列表区域 -->
    <div class="message-list" ref="messageListRef" @scroll="handleListScroll">
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
          <el-avatar :size="40" :style="{ backgroundColor: message.role === 'user' ? '#A25663' : '#67c23a' }">
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
          
          <!-- 工具调用痕迹：后端把上下文里 role=TOOL 的结果消息折叠到这里，不再单独渲染成一个空白助手气泡 -->
          <!-- 位置排在正文之前：推理 → 调工具 → 拿结果 → 给出回答，跟本轮实际发生的顺序一致 -->
          <div v-if="message.toolCalls?.length" class="tool-call-section">
            <el-collapse>
              <el-collapse-item :title="`🔧 本轮调用了 ${message.toolCalls.length} 次工具`" name="tools">
                <div
                  v-for="item in message.toolCalls"
                  :key="item.toolCallId"
                  class="tool-call-item"
                >
                  <div class="tool-call-header">
                    <span class="tool-call-name">{{ confirmToolLabel(item.toolName) }}</span>
                    <span :class="['tool-call-state', item.state === 'success' ? 'ok' : 'warn']">
                      {{ toolStateLabel(item.state) }}
                    </span>
                  </div>
                  <div v-if="item.summary" class="tool-call-summary">{{ item.summary }}</div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
          
          <!-- 文本阶段（出错时以告警样式展示失败原因） -->
          <div class="message-text" :class="{ 'message-error': message.error }" v-html="formatMessage(message.content)"></div>
          
          <!-- 产物卡片：一轮可以交付多个文件（报告 + 图表 + 归档），逐个列出来，一个都不丢 -->
          <div
            v-for="artifact in message.artifacts"
            :key="artifact.artifactId"
            class="file-attachment"
          >
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ artifact.fileName }}</span>
            <span class="file-size" v-if="artifact.fileSize">({{ formatFileSize(artifact.fileSize) }})</span>
            <el-button type="primary" link @click="handleDownloadFile(artifact)">
              <el-icon><Download /></el-icon>
              下载
            </el-button>
          </div>
          
          <!-- 人在回路：命中 ASK 规则后 Agent 已带着这些调用暂停本轮，必须在这里裁决会话才能继续 -->
          <div v-if="message.confirmRequests?.length" class="confirm-section">
            <div class="confirm-tip">
              <el-icon><Warning /></el-icon>
              <span>以下操作需要你确认后才会执行</span>
            </div>
            <div
              v-for="item in message.confirmRequests"
              :key="item.toolCallId"
              class="confirm-item"
            >
              <div class="confirm-item-header">
                <span class="confirm-tool-name">{{ confirmToolLabel(item.toolName) }}</span>
                <!-- 已裁决的条目不再渲染按钮：同一个待确认调用只认一次确认，重复提交会被服务端拒掉 -->
                <span v-if="item.decision" class="confirm-decided">
                  {{ item.decision === 'allow' ? '已允许本次' : '已拒绝' }}
                </span>
              </div>
              <div v-if="item.argumentSummary" class="confirm-args">{{ item.argumentSummary }}</div>
              <div v-if="!item.decision" class="confirm-actions">
                <el-button
                  type="success"
                  size="small"
                  :disabled="loading"
                  @click="handleConfirm(index, item.toolCallId, true)"
                >
                  允许本次
                </el-button>
                <el-button
                  size="small"
                  :disabled="loading"
                  @click="handleConfirm(index, item.toolCallId, false)"
                >
                  拒绝
                </el-button>
              </div>
            </div>
          </div>

          <!-- 消息操作按钮 -->
          <div class="message-actions" v-if="message.role === 'assistant'">
            <el-button size="small" text @click="handleCopy(getFullContent(message))">
              <el-icon><DocumentCopy /></el-icon>
              复制
            </el-button>
            <!-- 仅最后一条回答可重新生成：服务端回滚的是上下文里最后一条用户消息，
                 对更早的轮次点重生会误删最后一轮且旧问答仍留在上下文里；生成中也不展示 -->
            <el-button v-if="index === messages.length - 1 && !loading" size="small" text @click="handleRegenerate(index)">
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

      <!-- 悬浮「回到底部」：靠 sticky 钉在滚动区底边，只在未贴底时出现 -->
      <div class="jump-anchor">
        <transition name="jump-fade">
          <button
            v-if="showJumpToBottom"
            type="button"
            class="jump-to-bottom"
            title="回到底部"
            aria-label="回到底部"
            @click="handleJumpToBottom"
          >
            <el-icon :size="18"><ArrowDown /></el-icon>
          </button>
        </transition>
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
          <!-- 模型切换：选项由服务端 m_llm_model_config 配置下发，切换后自下一条消息生效 -->
          <div v-if="hasModels" class="model-picker">
            <span class="model-picker-label">模型</span>
            <el-select
              class="model-select"
              :model-value="currentModel"
              placeholder="默认模型"
              :loading="modelsLoading"
              :disabled="loading"
              :no-data-text="'暂无可选模型'"
              @change="handleModelChange"
            >
              <el-option-group
                v-for="group in groupedModels"
                :key="group.vendorName"
                :label="group.vendorName"
              >
                <el-option
                  v-for="item in group.options"
                  :key="item.modelName"
                  :label="item.displayName"
                  :value="item.modelName"
                >
                  <span class="model-option-name">{{ item.displayName }}</span>
                  <span class="model-option-extra">{{ item.vendorName }}{{ item.isDefault ? ' · 默认' : '' }}</span>
                </el-option>
              </el-option-group>
            </el-select>
          </div>

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
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
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
  Plus,
  ArrowDown,
  Warning
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
  },
  // 可选模型列表（父组件从 /llm/models 加载）
  models: {
    type: Array,
    default: () => []
  },
  // 当前选中的模型标识（对应后端 modelName）
  currentModel: {
    type: String,
    default: ''
  },
  // 模型列表加载中（下拉框 loading 态）
  modelsLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send', 'copy', 'regenerate', 'stop', 'download-file', 'new-conversation', 'change-model', 'confirm'])

const inputMessage = ref('')
const messageListRef = ref(null)

// 有可用模型才渲染下拉框（后端配置表为空时不占位，直接用服务端默认模型）
const hasModels = computed(() => Array.isArray(props.models) && props.models.length > 0)

// 按厂商分组渲染下拉：一个厂商可能提供多个模型
const groupedModels = computed(() => {
  const groups = []
  const indexByVendor = new Map()
  for (const item of props.models || []) {
    const vendorName = item.vendorName || item.vendorCode || '其他'
    if (!indexByVendor.has(vendorName)) {
      indexByVendor.set(vendorName, groups.length)
      groups.push({ vendorName, options: [] })
    }
    groups[indexByVendor.get(vendorName)].options.push(item)
  }
  return groups
})

// 切换模型：由父组件记录选择并在下一条消息生效
const handleModelChange = (modelName) => {
  emit('change-model', modelName)
  const item = (props.models || []).find(m => m.modelName === modelName)
  ElMessage.success(`已切换模型：${item?.displayName || modelName}`)
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + units[i]
}

// 下载单个产物：具体取哪个对象由父组件凭 artifactId 调接口，这里只把条目原样交上去
// （不再交整条 message：按消息ID 下载只能取到一个文件，也绕不开服务端的归属校验）
const handleDownloadFile = (artifact) => {
  emit('download-file', artifact)
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

// 待确认工具名的可读化：卡片要让用户看懂“要动我机器上的什么”，而不是一串下划线标识
// 除了自己的两个工具，harness 还会自动注册一批平台工具（execute / read_file / write_file 等），
// 不补映射的话确认卡片与工具调用痕迹上会直接显示裸英文标识
const confirmToolLabel = (toolName) => {
  const labels = {
    read_path_file_names: '读取目录下的文件列表',
    execute_shell_command: '执行 Shell 命令',
    query_city_weather: '查询城市天气',
    execute: '执行 Shell 命令（内置）',
    read_file: '读取文件内容',
    list_files: '列出文件',
    grep_files: '搜索文件内容',
    glob_files: '按通配符查找文件',
    write_file: '新建/覆盖文件',
    edit_file: '编辑文件',
    web_fetch: '抓取网页',
    web_search: '联网搜索',
    memory_search: '检索长期记忆',
    memory_get: '读取记忆文件',
    memory_save: '写入长期记忆',
    session_search: '检索历史会话'
  }
  return labels[toolName] || toolName || '未知操作'
}

// 工具结果状态的可读化：ToolResultState 一共 success/error/denied/interrupted/running 五个取值
const toolStateLabel = (state) => {
  const labels = {
    success: '成功',
    error: '失败',
    denied: '已拒绝',
    interrupted: '已中断',
    running: '执行中'
  }
  return labels[state] || state || '状态未知'
}

// 用户对单个待确认调用做出裁决，交由父组件发起恢复流
const handleConfirm = (index, toolCallId, confirmed) => {
  emit('confirm', { index, toolCallId, confirmed })
}

// 距底部多少像素内算「已贴底」：留出容差，避免平滑滚动过程和小数像素高度抖动
const AT_BOTTOM_RANGE = 60

const isAtBottom = ref(true)
const canScroll = ref(false)
// 是否跟随新内容贴底：用户往上翻时解除，回到底部或手动点按钮后恢复
const stickToBottom = ref(true)

// 只有内容确实溢出、且当前没在底部时，才展示快捷定位
const showJumpToBottom = computed(() => canScroll.value && !isAtBottom.value)

const measure = () => {
  const el = messageListRef.value
  if (!el) return
  const distanceToBottom = el.scrollHeight - el.clientHeight - el.scrollTop
  canScroll.value = el.scrollHeight - el.clientHeight > AT_BOTTOM_RANGE
  isAtBottom.value = distanceToBottom <= AT_BOTTOM_RANGE
}

const handleListScroll = () => {
  measure()
  stickToBottom.value = isAtBottom.value
}

// 滚动到底部。force=false 用于流式追加：用户正在往上翻看历史时，不再被强行拽回底部
const scrollToBottom = ({ force = true } = {}) => {
  // 调用方往往刚改过 messages，下一轮 DOM 更新后 scrollHeight 才是新值
  nextTick(() => {
    const el = messageListRef.value
    if (!el) return
    measure()
    if (!force && !stickToBottom.value) return
    el.scrollTop = el.scrollHeight
    stickToBottom.value = true
    isAtBottom.value = true
  })
}

const handleJumpToBottom = () => {
  const el = messageListRef.value
  if (!el) return
  stickToBottom.value = true
  el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
}

// 消息增删（发新消息、切会话、重新生成、加载历史）后重新测量，保证按钮显隐及时
watch(() => props.messages.length, () => nextTick(measure))

let resizeObserver = null

// 首次挂载先贴底，并开始监听尺寸变化
onMounted(() => {
  scrollToBottom()
  nextTick(measure)
  // 窗口缩放、侧边栏折叠等改变可视高度时重新测量
  if (typeof ResizeObserver !== 'undefined' && messageListRef.value) {
    resizeObserver = new ResizeObserver(measure)
    resizeObserver.observe(messageListRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
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

/* 悬浮快捷定位：零高度的 sticky 容器钉在滚动区底边，自身不占内容高度 */
.jump-anchor {
  position: sticky;
  bottom: 0;
  height: 0;
  display: flex;
  justify-content: flex-end;
  align-items: flex-end;
  z-index: 10;
  pointer-events: none;
}

.jump-to-bottom {
  pointer-events: auto;
  margin-bottom: 12px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 50%;
  background: #FBF7EE;
  color: #5C4A42;
  box-shadow: 0 4px 14px rgba(92, 74, 66, 0.22);
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  -webkit-user-select: none;
}

.jump-to-bottom:hover {
  background: #F5B199;
  color: #fff;
  transform: translateY(-2px);
}

.jump-fade-enter-active,
.jump-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.jump-fade-enter-from,
.jump-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
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
  background: #FBF7EE;
  color: #3D3730;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.message-item.user .message-content {
  background: #EBCB8B;
  color: #3E2F1A;
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
/* 与下一块（工具调用卡或正文）的间距：相邻块级元素的上下 margin 会合并取最大值，
   因此这里降到 8px 才能真正紧凑，单改 .tool-call-section 的 margin-top 不生效 */
.thinking-section {
  margin-bottom: 8px;
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
  /* 内容已经 markdown-it 渲染（breaks:true 会把源文本换行转成 <br>/<p>），
     若再用 pre-wrap 会把渲染结果里的换行符二次显示，导致段落间距过大 */
  white-space: normal;
  word-wrap: break-word;
  /* 限制展开高度：超长推理在内部滚动，折叠按钮不会随内容滚出视口 */
  max-height: 320px;
  overflow-y: auto;
  padding-right: 8px;
}

.thinking-content :deep(p) {
  margin: 4px 0;
}

.thinking-content::-webkit-scrollbar {
  width: 6px;
}

.thinking-content::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

.thinking-content::-webkit-scrollbar-track {
  background: transparent;
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

/* 流式异常收口后的提示（如模型额度用完）：弱化底色 + 柔和红色，与正文区分但不刺眼 */
.message-text.message-error {
  color: #c45656;
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
  border-radius: 4px;
  padding: 8px 12px;
}

/* 代码块样式（markdown-it 输出 <pre><code>） */
.message-text :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border: 1px solid rgba(0, 0, 0, 0.18);
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
  background: rgba(0, 0, 0, 0.07);
  border: 1px solid rgba(0, 0, 0, 0.14);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 0.9em;
  color: #b5335a;
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
  color: #FAFAF5;
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
  border: 1px solid rgba(0, 0, 0, 0.16);
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
  border-left: 4px solid #FAFAF5;
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

/* 工具调用痕迹（历史消息里折叠过来的工具结果）：夹在推理与正文之间，上下都要留出间距 */
.tool-call-section {
  margin: 8px 0 10px;
  padding: 6px 12px 0;
  background: #f5f7fa;
  border: 1px solid #dbe2ea;
  border-radius: 8px;
}

.tool-call-item {
  padding: 6px 0;
}

/* 第二条起加分隔线；同 confirm-item，不能用 :first-of-type */
.tool-call-item + .tool-call-item {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px dashed rgba(92, 74, 66, 0.15);
}

.tool-call-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tool-call-name {
  color: #5C4A42;
  font-size: 13px;
  font-weight: 600;
}

.tool-call-state {
  font-size: 12px;
  color: #64748b;
}

.tool-call-state.ok {
  color: #15803d;
}

.tool-call-state.warn {
  color: #b45309;
}

/* 工具输出可能很长，限高后内部滚动，不把气泡撑爆 */
.tool-call-summary {
  margin-top: 6px;
  max-height: 160px;
  overflow-y: auto;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.75);
  border-radius: 6px;
  color: #4b5563;
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 人在回路确认卡片 */
.confirm-section {
  margin-top: 12px;
  padding: 12px 14px;
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 8px;
}

.confirm-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #92400e;
  font-size: 13px;
  font-weight: 500;
}

.confirm-item {
  margin-top: 8px;
}

/* 第二条起加分隔线；不能用 :first-of-type，子元素全是 div 时它匹配不到 confirm-item */
.confirm-item + .confirm-item {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(180, 83, 9, 0.2);
}

.confirm-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.confirm-tool-name {
  color: #78350f;
  font-size: 13px;
  font-weight: 600;
}

.confirm-decided {
  color: #64748b;
  font-size: 12px;
}

/* 工具入参原文（命令 / 路径）等宽展示，用户能看清具体要动哪个目录 */
.confirm-args {
  margin-top: 6px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 6px;
  color: #4b5563;
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

.confirm-actions {
  margin-top: 10px;
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

/* 模型切换下拉 */
.model-picker {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-picker-label {
  font-size: 13px;
  color: #6B5B41;
}

.model-select {
  width: 200px;
}

.input-area :deep(.model-select .el-select__wrapper) {
  border-radius: 16px;
  background: #FBF7EE;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.1) inset;
}

.input-area :deep(.model-select .el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px #F5B199 inset;
}

/* 下拉选项：左侧模型名，右侧厂商（+默认标记） */
.model-option-name {
  display: inline-block;
}

.model-option-extra {
  float: right;
  margin-left: 24px;
  color: #A08E6C;
  font-size: 12px;
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

  .model-select {
    width: 140px;
  }
}
</style>
