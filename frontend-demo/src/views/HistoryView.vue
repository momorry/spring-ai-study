<template>
  <div class="history-sidebar">
    <!-- 侧边栏头部 -->
    <div class="sidebar-header">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索会话..."
        size="small"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
        class="search-input"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button text :icon="Search" @click="handleSearch" circle size="small" class="search-btn" />
    </div>

    <!-- 滚动容器 -->
    <div
      ref="scrollRef"
      v-infinite-scroll="loadMore"
      :infinite-scroll-disabled="scrollDisabled"
      :infinite-scroll-distance="30"
      class="session-scroll"
    >
      <div v-if="list.length" class="session-list">
        <div
          v-for="item in list"
          :key="item.id"
          class="session-item"
          :class="{ active: item.sessionId === currentSessionId }"
          @click="openSession(item)"
        >
          <el-icon v-if="item.pinned" class="pin-icon"><TopRight /></el-icon>
          <span class="session-title">{{ item.title || '未命名会话' }}</span>
          <div class="session-actions" @click.stop>
            <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, item)">
              <el-icon class="action-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <el-icon><Edit /></el-icon>
                    <span>重命名</span>
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <el-icon><Delete /></el-icon>
                    <span>删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-else-if="!loading && !loadError" description="暂无历史会话" :image-size="60" style="color: #6d7a8a;" />

      <!-- 加载提示 -->
      <div class="load-tip" v-if="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      <div class="load-tip error-tip" v-else-if="loadError" @click="refresh">
        <span>加载失败，点击重试</span>
      </div>
      <div class="load-tip" v-else-if="noMore && list.length">
        <span>— 已加载全部 —</span>
      </div>
    </div>

    <!-- 返回顶部按钮 -->
    <div class="back-to-top" v-show="showBackTop" @click="scrollToTop">
      <el-icon><ArrowUp /></el-icon>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Loading, ArrowUp, TopRight, MoreFilled, Edit, Delete } from '@element-plus/icons-vue'
import { pageChatSessions, renameChatSession, deleteChatSession } from '@/api/chatSession'

const props = defineProps({
  currentSessionId: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['select-session'])

const scrollRef = ref(null)
const list = ref([])
const allSessions = ref([]) // 存储所有会话用于搜索过滤
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const showBackTop = ref(false)
const loadError = ref(false)   // 标记加载是否失败
const searchKeyword = ref('')  // 搜索关键词
let consecutiveErrors = 0      // 连续失败次数

// 已加载全部（total 为 0 表示无数据，也视为加载完毕）
const noMore = computed(() => total.value === 0 || (total.value > 0 && list.value.length >= total.value) || loadError.value)
// 禁用滚动加载
const scrollDisabled = computed(() => loading.value || noMore.value)

// 加载一页数据
async function loadPage() {
  if (loading.value) return
  loading.value = true
  
  try {
    const data = await pageChatSessions({ pageNum: pageNum.value, pageSize })
    const records = data?.records || []
    
    if (pageNum.value === 1) {
      list.value = records
      allSessions.value = records // 保存所有会话用于搜索
    } else {
      list.value.push(...records)
      allSessions.value.push(...records)
    }
    
    total.value = Number(data?.total || 0)
    pageNum.value += 1
    consecutiveErrors = 0
    loadError.value = false
    
    // 只有在有数据且容器未撑满时才继续加载
    if (records.length > 0 && !noMore.value) {
      setTimeout(() => fillIfNotScrollable(), 100)
    }
  } catch (e) {
    consecutiveErrors++
    loadError.value = true
    // 仅在首次失败时提示，避免疯狂弹消息
    if (consecutiveErrors === 1) {
      ElMessage.error('加载历史会话失败，请检查后端服务是否启动')
    }
  } finally {
    loading.value = false
  }
}

// 容器未撑满时补页（添加最大重试次数防止无限循环）
let fillRetryCount = 0
const MAX_FILL_RETRIES = 3

function fillIfNotScrollable() {
  const el = scrollRef.value
  if (!el || noMore.value || loading.value) {
    fillRetryCount = 0
    return
  }
  
  // 增加重试计数
  fillRetryCount++
  if (fillRetryCount > MAX_FILL_RETRIES) {
    fillRetryCount = 0
    return
  }
  
  // 判断容器是否可滚动
  if (el.scrollHeight <= el.clientHeight + 10) {
    loadPage()
  } else {
    fillRetryCount = 0
  }
}

// 触底加载更多
function loadMore() {
  if (scrollDisabled.value) return
  loadPage()
}

// 搜索会话
function handleSearch() {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    // 清空搜索时恢复原始列表
    list.value = [...allSessions.value]
    return
  }
  
  // 过滤会话列表
  list.value = allSessions.value.filter(session => {
    const title = (session.title || '').toLowerCase()
    return title.includes(keyword)
  })
}

// 刷新：重置到第一页（清空搜索条件）
function refresh() {
  pageNum.value = 1
  total.value = 0
  list.value = []
  allSessions.value = []
  fillRetryCount = 0
  loadError.value = false
  consecutiveErrors = 0
  searchKeyword.value = ''
  loadPage()
}

// 刷新但保留搜索条件
function refreshPreserveSearch() {
  const keyword = searchKeyword.value.trim().toLowerCase()
  pageNum.value = 1
  total.value = 0
  list.value = []
  allSessions.value = []
  fillRetryCount = 0
  loadError.value = false
  consecutiveErrors = 0
  // 不重置 searchKeyword，保持筛选条件
  loadPage().then(() => {
    // 数据加载完成后，如果有搜索条件则重新过滤
    if (keyword) {
      list.value = allSessions.value.filter(session => {
        const title = (session.title || '').toLowerCase()
        return title.includes(keyword)
      })
    }
  })
}

// 打开会话
function openSession(item) {
  emit('select-session', item.sessionId)
}

// 操作菜单命令处理
async function handleCommand(command, item) {
  if (command === 'rename') {
    await handleRename(item)
  } else if (command === 'delete') {
    await handleDelete(item)
  }
}

// 重命名会话
async function handleRename(item) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的会话标题', '重命名会话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: item.title || '',
      inputPattern: /\S+/,
      inputErrorMessage: '标题不能为空'
    })
    
    const newTitle = value.trim()
    if (newTitle === (item.title || '')) return
    
    await renameChatSession(item.id, newTitle)
    item.title = newTitle
    ElMessage.success('重命名成功')
  } catch (e) {
    if (e !== 'cancel' && e?.toString() !== 'cancel') {
      ElMessage.error('重命名失败')
    }
  }
}

// 删除会话
async function handleDelete(item) {
  try {
    await ElMessageBox.confirm('确定要删除该会话吗？删除后无法恢复。', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deleteChatSession(item.id)
    ElMessage.success('删除成功')
    
    // 从列表中移除
    const index = list.value.findIndex(s => s.id === item.id)
    if (index !== -1) {
      list.value.splice(index, 1)
    }
    const allIndex = allSessions.value.findIndex(s => s.id === item.id)
    if (allIndex !== -1) {
      allSessions.value.splice(allIndex, 1)
    }
    
    // 如果删除的是当前选中的会话，清空当前会话ID
    if (props.currentSessionId === item.sessionId) {
      emit('select-session', null)
    }
  } catch (e) {
    if (e !== 'cancel' && e?.toString() !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 滚动到顶部
function scrollToTop() {
  if (scrollRef.value) {
    scrollRef.value.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

// 监听滚动，显示/隐藏返回顶部按钮
function handleScroll() {
  if (scrollRef.value) {
    showBackTop.value = scrollRef.value.scrollTop > 200
  }
}

// 监听当前会话变化，滚动到可见位置
watch(() => props.currentSessionId, (newId) => {
  if (!newId) return
  const index = list.value.findIndex(item => item.sessionId === newId)
  if (index !== -1 && scrollRef.value) {
    const items = scrollRef.value.querySelectorAll('.session-item')
    if (items[index]) {
      items[index].scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }
})

onMounted(() => {
  loadPage()
  if (scrollRef.value) {
    scrollRef.value.addEventListener('scroll', handleScroll)
  }
  // 监听 AI 回答完成/出错事件，自动刷新会话列表
  window.addEventListener('refresh-session-list', refreshPreserveSearch)
})

onUnmounted(() => {
  if (scrollRef.value) {
    scrollRef.value.removeEventListener('scroll', handleScroll)
  }
  // 移除事件监听
  window.removeEventListener('refresh-session-list', refreshPreserveSearch)
})
</script>

<style scoped>
.history-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #F9E9CD;
  position: relative;
  border-radius: 16px;
  overflow: hidden;
}

.sidebar-header {
  flex-shrink: 0;
  height: 48px;
  padding: 0 12px;
  background: rgba(0, 0, 0, 0.06);
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 搜索框样式 */
.search-input {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  box-shadow: none;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.search-input :deep(.el-input__wrapper:hover) {
  border-color: #F5B199;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: #F5B199;
}

.search-input :deep(.el-input__inner) {
  color: #5C4A42;
}

.search-input :deep(.el-input__inner::placeholder) {
  color: #909399;
}

/* 搜索按钮样式 */
.search-btn {
  color: #5C4A42;
  flex-shrink: 0;
}

.search-btn:hover {
  color: #FAFAF5;
  background: rgba(0, 0, 0, 0.06);
}

.header-title {
  font-size: 14px;
  font-weight: 600;
  color: #5C4A42;
}

.session-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
  color: #5C4A42;
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #FAFAF5;
}

.session-item.active {
  background: #F5B199;
  color: #fff;
}

.session-item .session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item .pin-icon {
  flex-shrink: 0;
  font-size: 12px;
  color: #f56c6c;
}

.session-item.active .pin-icon {
  color: #fff;
}

/* 会话操作按钮样式 */
.session-actions {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-actions {
  opacity: 1;
}

.session-actions .action-icon {
  font-size: 16px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.session-actions .action-icon:hover {
  background: rgba(0, 0, 0, 0.1);
}

.session-item.active .session-actions .action-icon {
  color: #fff;
}

.session-item.active .session-actions .action-icon:hover {
  background: rgba(255, 255, 255, 0.2);
}

.load-tip {
  text-align: center;
  padding: 12px 0;
  font-size: 12px;
  color: #909399;
}

.load-tip.error-tip {
  color: #f56c6c;
  cursor: pointer;
}

.load-tip.error-tip:hover {
  color: #f78989;
}

.load-tip .is-loading {
  font-size: 16px;
}

.back-to-top {
  position: absolute;
  bottom: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #F48C06, #FAFAF5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(232, 93, 4, 0.4);
  transition: all 0.3s;
  z-index: 10;
}

.back-to-top:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(232, 93, 4, 0.6);
}

/* 滚动条样式 */
.session-scroll::-webkit-scrollbar {
  width: 4px;
}

.session-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.session-scroll::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 2px;
}

.session-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.25);
}
</style>
