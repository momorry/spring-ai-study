<template>
  <el-container class="about-container">
    <el-header class="about-header">
      <el-button @click="goHome" type="primary">
        <el-icon><ArrowLeft /></el-icon>
        返回首页
      </el-button>
    </el-header>

    <el-main class="about-main">
      <el-card shadow="hover">
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
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}
</script>

<style scoped>
.about-container {
  min-height: 100vh;
  background: #f5f7fa;
}

.about-header {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.about-main {
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-header {
  font-size: 20px;
  font-weight: bold;
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
