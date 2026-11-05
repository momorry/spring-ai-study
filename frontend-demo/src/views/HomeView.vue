<template>
  <el-container class="home-container">
    <el-header class="home-header">
      <div class="logo">
        <el-icon :size="24"><HomeFilled /></el-icon>
        <span>Frontend Demo</span>
      </div>
      <el-menu
        :default-active="activeIndex"
        class="header-menu"
        mode="horizontal"
        @select="handleSelect"
      >
        <el-menu-item index="1">首页</el-menu-item>
        <el-menu-item index="2">关于</el-menu-item>
      </el-menu>
    </el-header>

    <el-main class="home-main">
      <div class="welcome-card">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>欢迎使用 Vue 3 + Element Plus</span>
            </div>
          </template>
          
          <div class="content">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-statistic title="Vue 版本" :value="vueVersion">
                  <template #prefix>
                    <el-icon><InfoFilled /></el-icon>
                  </template>
                </el-statistic>
              </el-col>
              <el-col :span="8">
                <el-statistic title="Element Plus" value="2.9.1">
                  <template #prefix>
                    <el-icon><Check /></el-icon>
                  </template>
                </el-statistic>
              </el-col>
              <el-col :span="8">
                <el-statistic title="Vite" value="6.0.5">
                  <template #prefix>
                    <el-icon><Lightning /></el-icon>
                  </template>
                </el-statistic>
              </el-col>
            </el-row>

            <el-divider />

            <div class="features">
              <h3>技术栈</h3>
              <el-tag type="primary" size="large">Vue 3</el-tag>
              <el-tag type="success" size="large">Element Plus</el-tag>
              <el-tag type="warning" size="large">Vite</el-tag>
              <el-tag type="danger" size="large">Pinia</el-tag>
              <el-tag type="info" size="large">Vue Router</el-tag>
            </div>

            <el-divider />

            <div class="actions">
              <el-button type="primary" @click="handleClick">
                <el-icon><Plus /></el-icon>
                点击测试
              </el-button>
              <el-button type="success" @click="showMessage">
                <el-icon><ChatDotRound /></el-icon>
                显示消息
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </el-main>

    <el-footer class="home-footer">
      <p>© 2026 Spring AI & AgentScope Multi-Module Project</p>
    </el-footer>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { version as vueVersion } from 'vue'

const activeIndex = ref('1')

const handleSelect = (key, keyPath) => {
  console.log(key, keyPath)
}

const handleClick = () => {
  ElMessage.success('按钮点击成功！')
}

const showMessage = () => {
  ElMessage({
    message: '欢迎使用 Element Plus！',
    type: 'info',
    duration: 3000
  })
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 0 20px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  background: linear-gradient(135deg, #F48C06, #E85D04);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-menu {
  border-bottom: none;
}

.home-main {
  flex: 1;
  background: #f5f7fa;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.welcome-card {
  max-width: 800px;
  width: 100%;
}

.card-header {
  font-size: 20px;
  font-weight: bold;
}

.content {
  padding: 20px 0;
}

.features {
  text-align: center;
}

.features h3 {
  margin-bottom: 20px;
  color: #303133;
}

.features .el-tag {
  margin: 0 10px 10px 0;
}

.actions {
  text-align: center;
  margin-top: 20px;
}

.home-footer {
  text-align: center;
  background: #fff;
  color: #909399;
  padding: 20px;
}
</style>
