# 📊 多模块项目总览

## 🎯 项目架构

```
spring-ai-embabel/                          # 父项目
│
├── pom.xml                                  # Maven 父 POM
├── README.md                                # 项目主文档
├── .gitignore                               # Git 忽略配置
│
├── spring-ai-demo/                          # 后端模块 1
│   ├── pom.xml
│   └── src/main/java/com/moli/spring/ai/
│       ├── AgentExample.java
│       ├── agents/                          # 7 个 Agent 示例
│       ├── base/                            # 基础工具
│       ├── hooks/                           # Hook 示例
│       ├── interceptor/                     # 拦截器
│       ├── prompt/                          # 动态提示词
│       └── tools/                           # 工具定义
│
├── agent-scope-demo/                        # 后端模块 2
│   ├── pom.xml
│   └── src/main/java/com/moli/agent/scope/
│       ├── AgentScopeTest.java
│       ├── AgentSession.java
│       ├── middleware/
│       └── model/
│
└── frontend-demo/                           # 前端模块
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── api/                             # API 接口
        ├── components/                      # 公共组件
        ├── router/                          # 路由配置
        ├── stores/                          # Pinia 状态管理
        ├── utils/                           # 工具函数
        ├── views/                           # 页面视图
        ├── App.vue
        └── main.js
```

## 📋 模块详情

### 1️⃣ spring-ai-demo (后端模块 - Spring AI)

**技术栈：**
- Java 21
- Spring Boot 3.5.4
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.0

**功能：**
- Spring AI 集成示例
- 多种 Agent 实现
- 工具调用示例
- 拦截器和钩子

**构建工具：** Maven

---

### 2️⃣ agent-scope-demo (后端模块 - AgentScope)

**技术栈：**
- Java 21
- AgentScope Harness 2.0.1
- AgentScope Extensions 2.0.4

**功能：**
- AgentScope 2.0 框架示例
- ReAct Agent 模式
- 中间件示例
- 模型配置

**构建工具：** Maven

---

### 3️⃣ frontend-demo (前端模块)

**技术栈：**
- Node.js >= 18.0.0
- Vue 3.5.13
- Element Plus 2.9.1
- Vite 6.0.5
- Pinia 2.3.0
- Vue Router 4.5.0
- Axios 1.7.9

**功能：**
- 现代化 Web 应用
- Element Plus UI 组件
- 状态管理
- 路由系统
- API 集成
- 示例页面

**构建工具：** Vite + npm

---

## 🚀 快速开始

### 后端模块 (Spring AI & AgentScope)

**前置要求：**
- Java 21
- Maven 3.6+

**编译命令：**
```bash
# 编译整个后端项目
mvn clean install

# 单独编译 Spring AI 模块
mvn clean install -pl spring-ai-demo

# 单独编译 AgentScope 模块
mvn clean install -pl agent-scope-demo
```

---

### 前端模块

**前置要求：**
- Node.js >= 18.0.0
- npm >= 9.0.0

**安装和运行：**
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

**访问地址：** http://localhost:3000

---

## 🔗 模块间关系

### 独立性
- ✅ 三个模块完全独立
- ✅ 互不影响
- ✅ 可以单独开发、测试、部署

### 集成性
- ✅ 前端可以通过 API 与后端通信
- ✅ 已配置 API 代理
- ✅ 支持跨域请求

### API 代理配置

前端开发服务器已配置代理：

```
前端请求: http://localhost:3000/api/*
   ↓
代理转发: http://localhost:8080/*
   ↓
后端服务: Spring Boot 应用
```

---

## 📊 技术栈对比

| 模块 | 语言 | 框架 | 构建工具 | 用途 |
|------|------|------|----------|------|
| spring-ai-demo | Java 21 | Spring Boot | Maven | Spring AI 示例 |
| agent-scope-demo | Java 21 | AgentScope | Maven | AgentScope 示例 |
| frontend-demo | JavaScript | Vue 3 | Vite | Web 前端界面 |

---

## 📁 项目统计

### 文件数量
- **后端模块**: 23 个 Java 文件
- **前端模块**: 15+ 个核心文件
- **配置文件**: 10+ 个
- **文档文件**: 8 个

### 代码行数
- **后端代码**: ~1000+ 行
- **前端代码**: ~600+ 行
- **配置文件**: ~300+ 行
- **文档**: ~1500+ 行

### 依赖数量
- **spring-ai-demo**: 5 个主要依赖
- **agent-scope-demo**: 4 个主要依赖
- **frontend-demo**: 6 个核心依赖 + 6 个开发依赖

---

## 🎯 使用场景

### 场景 1: 学习 Spring AI
```bash
cd spring-ai-demo
# 学习和实践 Spring AI 框架
```

### 场景 2: 学习 AgentScope
```bash
cd agent-scope-demo
# 学习和实践 AgentScope 2.0 框架
```

### 场景 3: 开发前端应用
```bash
cd frontend-demo
npm run dev
# 开发 Web 界面
```

### 场景 4: 全栈开发
```bash
# 终端 1: 启动后端
mvn spring-boot:run -pl spring-ai-demo

# 终端 2: 启动前端
cd frontend-demo
npm run dev

# 访问 http://localhost:3000
```

---

## 📚 文档导航

### 主文档
- [README.md](./README.md) - 项目总体说明

### 配置文档
- [JAVA_CONFIG.md](./JAVA_CONFIG.md) - Java 版本配置指南
- [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md) - 项目结构说明
- [COMPLETION_REPORT.md](./COMPLETION_REPORT.md) - 后端拆分完成报告

### 前端文档
- [frontend-demo/README.md](./frontend-demo/README.md) - 前端模块详细说明
- [FRONTEND_COMPLETION.md](./FRONTEND_COMPLETION.md) - 前端创建完成报告

---

## ✨ 核心优势

### 1. 模块化设计
- 清晰的模块划分
- 完全独立的依赖
- 便于维护和扩展

### 2. 技术栈现代
- 最新的框架版本
- 主流的技术选型
- 符合行业标准

### 3. 开发体验优秀
- 完善的文档
- 示例代码丰富
- 配置简单明了

### 4. 集成便利
- API 代理已配置
- 跨域问题已解决
- 前后端分离架构

---

## 🎉 总结

这是一个功能完整的多模块项目，包含：

- ✅ **2 个后端模块** - Spring AI 和 AgentScope 示例
- ✅ **1 个前端模块** - Vue 3 + Element Plus 应用
- ✅ **完整的项目结构** - 清晰的文件组织
- ✅ **详尽的文档** - 8 个文档文件
- ✅ **示例代码** - 可直接运行的示例
- ✅ **开发配置** - 开箱即用的开发环境

现在你可以：
1. 学习后端 AI 框架
2. 开发前端 Web 应用
3. 构建全栈 AI 应用

祝你开发愉快！🚀
