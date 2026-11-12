<template>
  <el-container class="about-container">
    <el-main class="about-main">
      <el-card shadow="hover" class="about-card">
        <template #header>
          <div class="card-header">
            <span>关于我</span>
          </div>
        </template>

        <div class="profile">
          <el-avatar :size="56" icon="UserFilled" class="profile-avatar" />
          <div class="profile-info">
            <p class="profile-row">
              <span class="profile-label">作者</span>
              <span class="profile-name">moli</span>
            </p>
            <p class="profile-row">
              <span class="profile-label">爱好</span>
              <span class="profile-text">游戏，摄影，看书，没事的时候喜欢一个人到处走走，看看树，感受风。</span>
            </p>
          </div>
        </div>

        <el-divider />

        <div class="signature">
          <h3>签名</h3>
          <blockquote class="signature-text">
            而风起的时候，又总让人觉得世界其实本来如此——世界本来就应该有这样的大风。我在半山腰往下看，再抬头往高处看。我看到全世界都是一场透明的倾斜，全世界都在倾向风去的方向。我的头发也往那边飘扬，我的心在原地挣扎，也充满了想要过去的渴望。
          </blockquote>
        </div>
      </el-card>

      <!-- 原首页内容搬迁至此 -->
      <el-card shadow="hover" class="about-card">
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

      <el-card shadow="hover" class="about-card">
        <template #header>
          <div class="card-header">
            <span>关于项目</span>
          </div>
        </template>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="项目名称">
            Spring AI & AgentScope Multi-Module Project
          </el-descriptions-item>
          <el-descriptions-item label="前端模块">
            frontend-demo
          </el-descriptions-item>
          <el-descriptions-item label="技术栈">
            <el-tag>Vue 3</el-tag>
            <el-tag type="success">Element Plus</el-tag>
            <el-tag type="warning">Vite</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Node 版本要求">
            >= 18.0.0
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            2026-11-02
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <div class="project-structure">
          <h3>项目结构</h3>
          <el-timeline>
            <el-timeline-item timestamp="父模块" placement="top" type="primary">
              <el-card>
                <h4>spring-ai-embabel</h4>
                <p>多模块父项目</p>
              </el-card>
            </el-timeline-item>
            <el-timeline-item timestamp="后端模块 1" placement="top" type="success">
              <el-card>
                <h4>spring-ai-demo</h4>
                <p>Spring AI 演示模块</p>
              </el-card>
            </el-timeline-item>
            <el-timeline-item timestamp="后端模块 2" placement="top" type="warning">
              <el-card>
                <h4>agent-scope-demo</h4>
                <p>AgentScope 2.0 演示模块</p>
              </el-card>
            </el-timeline-item>
            <el-timeline-item timestamp="前端模块" placement="top" type="danger">
              <el-card>
                <h4>frontend-demo</h4>
                <p>Vue 3 + Element Plus 前端模块</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { version as vueVersion } from 'vue'

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
.about-container {
  height: 100%;
  background: #F9E9CD;
}

.about-main {
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  overflow-y: auto;
  background: #F9E9CD;
}

/* 关于我、原首页内容、项目介绍三张卡片上下堆叠，限宽保持可读性 */
.about-card {
  width: 100%;
  max-width: 900px;
  flex-shrink: 0;
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

/* 关于我：作者信息与签名 */
.profile {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.profile-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #F48C06, #F5B199);
  color: #fff;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-row {
  margin: 0 0 10px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.profile-row:last-child {
  margin-bottom: 0;
}

.profile-label {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  color: #5C4A42;
  background: #F6E7C9;
}

.profile-name {
  font-size: 16px;
  font-weight: 600;
  color: #3E2F1A;
}

.profile-text {
  color: #5C4A42;
  line-height: 1.7;
}

.signature h3 {
  margin: 0 0 12px;
  color: #303133;
}

.signature-text {
  margin: 0;
  padding: 14px 18px;
  border-left: 3px solid #F5B199;
  border-radius: 0 8px 8px 0;
  background: #FBF7EE;
  color: #5C4A42;
  font-size: 15px;
  line-height: 1.9;
  letter-spacing: 0.5px;
  text-indent: 2em;
}

.project-structure {
  margin-top: 20px;
}

.project-structure h3 {
  margin-bottom: 20px;
  color: #303133;
}

.el-timeline {
  margin-top: 20px;
}
</style>
