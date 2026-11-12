<template>
  <el-container class="chat-layout">
    <!-- 左侧导航菜单 -->
    <el-aside :width="sidebarWidth" class="sidebar">
      <div class="logo">
        <el-icon :size="24"><Monitor /></el-icon>
        <span v-if="!isSidebarCollapsed">青梧小筑</span>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        :collapse="isSidebarCollapsed"
        router
        @select="handleMenuSelect"
      >
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>
        
        <el-menu-item index="/chat/react-agent">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>对话聊天</template>
        </el-menu-item>
      </el-menu>
      
      <!-- 历史会话面板 -->
      <div class="history-panel" v-show="!isSidebarCollapsed">
        <div class="panel-toggle" @click="toggleHistoryPanel">
          <el-icon><Clock /></el-icon>
          <span>历史会话</span>
          <el-icon class="toggle-arrow" :class="{ expanded: isHistoryExpanded }"><ArrowDown /></el-icon>
        </div>
        <div class="panel-content" v-show="isHistoryExpanded">
          <HistoryView 
            :current-session-id="currentSessionId"
            @select-session="handleSelectSession"
          />
        </div>
      </div>
      
      <!-- 折叠按钮 -->
      <div class="collapse-btn" @click="toggleSidebar">
        <el-icon v-if="isSidebarCollapsed"><DArrowRight /></el-icon>
        <el-icon v-else><DArrowLeft /></el-icon>
      </div>
      
      <!-- 关于菜单（放在最下方） -->
      <div class="sidebar-bottom-menu" v-if="!isSidebarCollapsed">
        <div 
          class="bottom-menu-item" 
          :class="{ 'is-active': activeMenu === '/about' }"
          @click="router.push('/about')"
        >
          <el-icon><InfoFilled /></el-icon>
          <span>关于</span>
        </div>
      </div>
      
      <div class="sidebar-footer" v-if="!isSidebarCollapsed">
        <el-divider />
        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="user-info">
            <el-avatar :size="32" icon="UserFilled" />
            <span class="user-name">{{ displayName }}</span>
            <el-icon class="user-caret"><ArrowUp /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-aside>

    <!-- 右侧主内容区 -->
    <el-container class="main-container">
      <el-header class="header">
        <div class="header-content">
          <div class="header-left">
            <el-button 
              v-if="isSidebarCollapsed" 
              text 
              @click="toggleSidebar"
              class="expand-btn"
            >
              <el-icon><DArrowRight /></el-icon>
            </el-button>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          
          <div class="header-right">
            <!-- 清空对话功能已移除 -->
          </div>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, watch, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Refresh, EditPen, HomeFilled, ChatDotRound, InfoFilled, 
  Clock, ArrowDown, ArrowUp, DArrowLeft, DArrowRight, SwitchButton, Monitor
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import HistoryView from '@/views/HistoryView.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = ref('/chat/react-agent')
const isSidebarCollapsed = ref(false)
const isHistoryExpanded = ref(true)
const currentSessionId = ref(null)

// 提供给子组件使用
provide('currentSessionId', currentSessionId)

// 当前登录用户昵称
const displayName = computed(() => userStore.userInfo?.nickName || '未登录')

// 侧边栏宽度
const sidebarWidth = computed(() => {
  if (isSidebarCollapsed.value) return '64px'
  return '280px'
})

// 页面标题映射
const pageTitles = {
  '/home': '首页',
  '/chat/agent': 'Agent',
  '/about': '关于'
}

const pageTitle = computed(() => {
  return pageTitles[route.path] || '青梧小筑'
})

const isChatPage = computed(() => {
  return route.path.startsWith('/chat/')
})

// 切换侧边栏折叠
function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

// 切换历史面板展开
function toggleHistoryPanel() {
  isHistoryExpanded.value = !isHistoryExpanded.value
}

// 选择历史会话
function handleSelectSession(sessionId) {
  currentSessionId.value = sessionId
  // 如果不在对话页面，跳转到对话页面
  if (!route.path.startsWith('/chat/')) {
    router.push('/chat/react-agent')
  }
}

// 用户下拉菜单操作
const handleUserCommand = async (command) => {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.replace({ name: 'login' })
  } catch {
    // 用户取消
  }
}

// 监听路由变化
watch(() => route.path, (newPath) => {
  activeMenu.value = newPath
}, { immediate: true })

const handleMenuSelect = (index) => {
  activeMenu.value = index
}


</script>

<style scoped>
.chat-layout {
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  background: #F9E9CD;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.3s ease;
  position: relative;
  border-radius: 16px;
  margin: 8px;
  height: calc(100vh - 16px);
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  color: #5C4A42;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.sidebar-menu {
  flex-shrink: 0;
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #5C4A42;
  --el-menu-active-color: #fff;
  --el-menu-hover-bg-color: rgba(0, 0, 0, 0.06);
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar-menu .el-menu-item.is-active {
  background: #F5B199 !important;
  color: #fff !important;
}

/* 历史面板样式 */
.history-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  margin-top: 8px;
}

.panel-toggle {
  height: 44px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #5C4A42;
  transition: all 0.2s;
  flex-shrink: 0;
  user-select: none;
  -webkit-user-select: none;
}

.panel-toggle:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #FAFAF5;
}

.panel-toggle .toggle-arrow {
  margin-left: auto;
  transition: transform 0.3s;
}

.panel-toggle .toggle-arrow.expanded {
  transform: rotate(180deg);
}

.panel-content {
  flex: 1;
  overflow: hidden;
  background: #F9E9CD;
  border-radius: 16px;
}

/* 折叠按钮 */
.collapse-btn {
  position: absolute;
  top: 50%;
  right: -14px;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #5C4A42;
  transition: all 0.2s;
  z-index: 100;
  user-select: none;
  -webkit-user-select: none;
}

.collapse-btn:hover {
  background: #F5B199;
  color: #fff;
}

.sidebar-footer {
  flex-shrink: 0;
  padding: 12px;
}

/* 底部菜单区域 */
.sidebar-bottom-menu {
  flex-shrink: 0;
  padding: 8px 0 0 0;
}

.bottom-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 4px;
  cursor: pointer;
  color: #5C4A42;
  font-size: 14px;
  transition: all 0.2s;
  background: transparent !important;
  -webkit-text-fill-color: #5C4A42 !important;
  user-select: none;
  -webkit-user-select: none;
}

.bottom-menu-item:hover {
  background: rgba(0, 0, 0, 0.06) !important;
  color: #FAFAF5 !important;
  -webkit-text-fill-color: #FAFAF5 !important;
}

.bottom-menu-item.is-active {
  background: #F5B199 !important;
  color: #fff !important;
  -webkit-text-fill-color: #fff !important;
}

.sidebar-footer .el-divider {
  background-color: rgba(0, 0, 0, 0.08);
  margin: 0 0 12px 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 16px;
  background: rgba(0, 0, 0, 0.04);
  cursor: pointer;
  outline: none;
  user-select: none;
  -webkit-user-select: none;
}

.user-info .user-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-info .user-caret {
  color: #5C4A42;
}

.user-info span {
  font-size: 14px;
  color: #5C4A42;
}

.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 8px 8px 8px 0;
  border-radius: 16px;
  background: #F9E9CD;
  height: calc(100vh - 16px);
}

.header {
  background: #F9E9CD;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
  height: 60px;
  color: #fff;
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #000;
}

.header-left :deep(.el-breadcrumb__inner) {
  color: #000;
}

.header-left :deep(.el-breadcrumb__separator) {
  color: rgba(0, 0, 0, 0.4);
}

.expand-btn {
  font-size: 18px;
}

.header-right {
  display: flex;
  gap: 12px;
}

.header-right :deep(.el-button) {
  color: #000;
}

.header-right :deep(.el-button:hover) {
  color: #FAFAF5;
}

.main-content {
  flex: 1;
  background: #F9E9CD;
  padding: 0;
  overflow: hidden;
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 滚动条样式 */
.sidebar-menu::-webkit-scrollbar {
  width: 6px;
}

.sidebar-menu::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.04);
}

.sidebar-menu::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

.sidebar-menu::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.25);
}
</style>
