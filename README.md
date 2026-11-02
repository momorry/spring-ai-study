# Spring AI & AgentScope 2.0 多模块项目

这是一个包含 Spring AI、AgentScope 2.0 和 Vue 3 前端演示模块的多模块项目。

## 项目结构

```
spring-ai-embabel/
├── pom.xml                    # 父模块 POM
├── spring-ai-demo/            # Spring AI 演示模块
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/moli/spring/ai/
│       └── resources/
├── agent-scope-demo/          # AgentScope 2.0 演示模块
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/moli/agent/scope/
│       └── resources/
└── frontend-demo/             # 前端演示模块
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── components/
        ├── views/
        ├── router/
        ├── stores/
        ├── api/
        └── utils/
```

## 模块说明

### 1. spring-ai-demo

Spring AI 演示模块，展示如何使用 Spring AI 和 Spring AI Alibaba 构建 AI 应用。

**主要特性：**
- 集成 OpenAI 兼容 API
- 使用 Spring AI Alibaba Agent Framework
- 包含多个 Agent 示例（ReAct Agent、结构化输出等）
- 支持流式输出

**依赖：**
- Spring Boot 3.5.4
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.0

### 2. agent-scope-demo

AgentScope 2.0 演示模块，展示如何使用独立的 AgentScope 框架构建 AI Agent。

**主要特性：**
- 使用 AgentScope Harness
- 集成 OpenAI 兼容模型
- 支持 ReAct Agent 模式
- 独立于 Spring AI，完全自主的框架

**依赖：**
- AgentScope Harness 2.0.1
- AgentScope Extensions Model OpenAI 2.0.4

### 3. frontend-demo

前端演示模块，基于 Vue 3 和 Element Plus 构建现代化 Web 应用。

**主要特性：**
- Vue 3 Composition API
- Element Plus UI 组件库
- Vite 极速构建工具
- Pinia 状态管理
- Vue Router 路由管理
- Axios HTTP 客户端
- 完整的 API 封装和工具函数

**依赖：**
- Vue 3.5.13
- Element Plus 2.9.1
- Vite 6.0.5
- Pinia 2.3.0
- Vue Router 4.5.0
- Axios 1.7.9

**前置要求：**
- Node.js >= 18.0.0
- npm >= 9.0.0

## 快速开始

### 编译整个项目

```bash
mvn clean install
```

### 单独编译某个模块

```bash
# 编译 Spring AI 模块
mvn clean install -pl spring-ai-demo

# 编译 AgentScope 模块
mvn clean install -pl agent-scope-demo
```

### 运行前端模块

```bash
# 进入前端目录
cd frontend-demo

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

访问 http://localhost:3000 查看前端应用

### 运行示例

#### Spring AI Demo

1. 配置环境变量（在 `spring-ai-demo/src/main/resources/.env` 中）：
```env
OPENAI_API_KEY=your-api-key
OPENAI_BASE_URL=http://your-api-endpoint
OPENAI_MODEL_NAME=your-model-name
```

2. 运行 Spring AI 示例类

#### AgentScope Demo

1. 在代码中配置模型参数（参考 `Model.java`）

2. 运行 AgentScope 示例类

## 技术栈

### 后端
- **Java**: 21
- **Spring Boot**: 3.5.4
- **Spring AI**: 1.1.2
- **AgentScope**: 2.0.1
- **Maven**: 多模块项目

### 前端
- **Node.js**: >= 18.0.0
- **Vue**: 3.5.13
- **Element Plus**: 2.9.1
- **Vite**: 6.0.5
- **Pinia**: 2.3.0
- **Vue Router**: 4.5.0
- **Axios**: 1.7.9

## 说明

这三个模块是完全独立的，互不影响：
- `spring-ai-demo` 依赖 Spring AI 生态系统
- `agent-scope-demo` 依赖 AgentScope 框架
- `frontend-demo` 是独立的前端项目

你可以根据需要选择使用其中某个模块，或者组合使用：
- 使用后端模块进行 AI Agent 开发
- 使用前端模块构建 Web 界面
- 前后端结合构建完整的 AI 应用
