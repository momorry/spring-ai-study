<template>
  <el-container class="chat-layout">
    <!-- 左侧导航菜单 -->
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <el-icon :size="24"><Monitor /></el-icon>
        <span>AI Chat Demo</span>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        router
        @select="handleMenuSelect"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        
        <el-sub-menu index="/chat">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>对话聊天</span>
          </template>
          <el-menu-item index="/chat/react-agent">
            <el-icon><SetUp /></el-icon>
            <span>ReAct Agent</span>
          </el-menu-item>
          <el-menu-item index="/chat/harness-agent">
            <el-icon><Connection /></el-icon>
            <span>Harness Agent</span>
          </el-menu-item>
        </el-sub-menu>
        
        <el-menu-item index="/about">
          <el-icon><InfoFilled /></el-icon>
          <span>关于</span>
        </el-menu-item>
      </el-menu>
      
      <div class="sidebar-footer">
        <el-divider />
        <div class="user-info">
          <el-avatar :size="32" icon="UserFilled" />
          <span>访客用户</span>
        </div>
      </div>
    </el-aside>

    <!-- 右侧主内容区 -->
    <el-container class="main-container">
      <el-header class="header">
        <div class="header-content">
          <div class="header-left">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-if="isChatPage">对话聊天</el-breadcrumb-item>
              <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          
          <div class="header-right">
            <el-button type="primary" text @click="handleClearChat" v-if="isChatPage">
              <el-icon><Delete /></el-icon>
              清空对话
            </el-button>
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
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const activeMenu = ref('/')

// 页面标题映射
const pageTitles = {
  '/': '首页',
  '/chat/react-agent': 'ReAct Agent',
  '/chat/harness-agent': 'Harness Agent',
  '/about': '关于'
}

const pageTitle = computed(() => {
  return pageTitles[route.path] || 'AI Chat Demo'
})

const isChatPage = computed(() => {
  return route.path.startsWith('/chat/')
})

// 监听路由变化
watch(() => route.path, (newPath) => {
  activeMenu.value = newPath
}, { immediate: true })

const handleMenuSelect = (index) => {
  activeMenu.value = index
}

const handleClearChat = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清空当前对话记录吗？此操作不可恢复。',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    ElMessage.success('对话已清空')
    // 这里可以触发自定义事件来清空对话
    window.dispatchEvent(new CustomEvent('clear-chat'))
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.chat-layout {
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  background: #304156;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  background: #304156;
  --el-menu-bg-color: #304156;
  --el-menu-text-color: #bfcbd9;
  --el-menu-active-color: #409eff;
  --el-menu-hover-bg-color: #263445;
  overflow-y: auto;
}

.sidebar-menu .el-menu-item.is-active {
  background-color: #409eff !important;
  color: #fff !important;
}

.sidebar-footer {
  flex-shrink: 0;
  padding: 12px;
}

.sidebar-footer .el-divider {
  background-color: rgba(255, 255, 255, 0.1);
  margin: 0 0 12px 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
}

.user-info span {
  font-size: 14px;
  color: #bfcbd9;
}

.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
  height: 60px;
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  flex: 1;
}

.header-right {
  display: flex;
  gap: 12px;
}

.main-content {
  flex: 1;
  background: #f5f7fa;
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
  background: rgba(255, 255, 255, 0.1);
}

.sidebar-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
}

.sidebar-menu::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.5);
}
</style>
