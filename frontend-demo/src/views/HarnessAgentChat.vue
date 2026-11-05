<template>
  <div class="harness-agent-page">
    <div class="coming-soon">
      <el-result
        icon="info"
        title="Harness Agent"
        sub-title="此功能正在开发中，敬请期待..."
      >
        <template #extra>
          <el-card class="info-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <el-icon :size="24" color="#E85D04"><Connection /></el-icon>
                <span>关于 Harness Agent</span>
              </div>
            </template>
            
            <el-descriptions :column="1" border>
              <el-descriptions-item label="名称">
                Harness Agent
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag type="warning">开发中</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="预计接口">
                <code>/harnessagent/conversation</code>
              </el-descriptions-item>
              <el-descriptions-item label="功能描述">
                基于 AgentScope Harness 框架的智能助手，支持工具调用、记忆管理等高级功能
              </el-descriptions-item>
            </el-descriptions>
            
            <el-divider />
            
            <div class="features">
              <h4>即将支持的功能：</h4>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-tag type="success" effect="plain">✓ 多轮对话</el-tag>
                </el-col>
                <el-col :span="12">
                  <el-tag type="success" effect="plain">✓ 工具调用</el-tag>
                </el-col>
                <el-col :span="12">
                  <el-tag type="success" effect="plain">✓ 记忆管理</el-tag>
                </el-col>
                <el-col :span="12">
                  <el-tag type="success" effect="plain">✓ 流式输出</el-tag>
                </el-col>
                <el-col :span="12">
                  <el-tag type="success" effect="plain">✓ 文件处理</el-tag>
                </el-col>
                <el-col :span="12">
                  <el-tag type="success" effect="plain">✓ 自定义中间件</el-tag>
                </el-col>
              </el-row>
            </div>
          </el-card>
          
          <div class="actions">
            <el-button type="primary" @click="goToReactAgent">
              <el-icon><Switch /></el-icon>
              前往 ReAct Agent
            </el-button>
            <el-button @click="goHome">
              <el-icon><HomeFilled /></el-icon>
              返回首页
            </el-button>
          </div>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goToReactAgent = () => {
  router.push('/chat/react-agent')
}

const goHome = () => {
  router.push('/')
}
</script>

<style scoped>
.harness-agent-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  padding: 20px;
}

.coming-soon {
  max-width: 800px;
  width: 100%;
}

.info-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
}

.features {
  margin-top: 16px;
}

.features h4 {
  margin-bottom: 12px;
  color: #303133;
}

.features .el-tag {
  margin: 4px;
  width: calc(100% - 8px);
  justify-content: center;
}

.actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

/* 动画效果 */
.coming-soon {
  animation: fadeIn 0.5s ease-in;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
