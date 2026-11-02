# Frontend Demo - Vue 3 + Element Plus

这是一个基于 **Vue 3** 和 **Element Plus** 的前端演示模块。

## 🚀 技术栈

- **框架**: Vue 3.5.13
- **UI 库**: Element Plus 2.9.1
- **构建工具**: Vite 6.0.5
- **状态管理**: Pinia 2.3.0
- **路由**: Vue Router 4.5.0
- **HTTP 客户端**: Axios 1.7.9
- **代码规范**: ESLint + Prettier

## 📋 前置要求

- **Node.js**: >= 18.0.0
- **npm**: >= 9.0.0

## 🛠️ 安装

```bash
# 进入前端模块目录
cd frontend-demo

# 安装依赖
npm install
```

## 🎯 开发

```bash
# 启动开发服务器
npm run dev
```

访问 http://localhost:3000 查看应用

## 📦 构建

```bash
# 生产环境构建
npm run build

# 预览生产环境构建
npm run preview
```

## 🔍 代码检查和格式化

```bash
# ESLint 检查并自动修复
npm run lint

# Prettier 格式化代码
npm run format
```

## 📁 项目结构

```
frontend-demo/
├── public/                  # 静态资源
│   └── favicon.ico
├── src/
│   ├── api/                 # API 接口
│   │   └── user.js          # 用户相关接口示例
│   ├── assets/              # 资源文件
│   │   └── styles/          # 样式文件
│   ├── components/          # 公共组件
│   ├── router/              # 路由配置
│   │   └── index.js
│   ├── stores/              # Pinia 状态管理
│   │   └── counter.js       # 计数器 store 示例
│   ├── utils/               # 工具函数
│   │   └── request.js       # Axios 封装
│   ├── views/               # 页面视图
│   │   ├── HomeView.vue     # 首页
│   │   └── AboutView.vue    # 关于页
│   ├── App.vue              # 根组件
│   └── main.js              # 入口文件
├── .env.example             # 环境变量示例
├── .gitignore               # Git 忽略配置
├── index.html               # HTML 模板
├── package.json             # 项目配置
├── README.md                # 说明文档
└── vite.config.js           # Vite 配置
```

## 🎨 功能特性

### 1. Element Plus 集成

- ✅ 完整引入 Element Plus
- ✅ 中文语言包配置
- ✅ 所有图标已注册，可直接使用

### 2. 路由配置

- ✅ Vue Router 4
- ✅ 历史模式 (history mode)
- ✅ 路由懒加载
- ✅ 示例路由配置

### 3. 状态管理

- ✅ Pinia 状态管理
- ✅ 示例 store (counter)
- ✅ 支持 Composition API 风格

### 4. HTTP 请求

- ✅ Axios 封装
- ✅ 请求/响应拦截器
- ✅ 统一的错误处理
- ✅ 自动添加 token

### 5. 开发体验

- ✅ Vite 极速启动
- ✅ 热模块替换 (HMR)
- ✅ ESLint + Prettier 代码规范
- ✅ 路径别名 `@` 指向 `src`

## 🔧 配置说明

### Vite 配置

`vite.config.js` 中已配置：

- **路径别名**: `@` → `./src`
- **开发服务器端口**: 3000
- **API 代理**: `/api` → `http://localhost:8080`

### 环境变量

复制 `.env.example` 为 `.env`：

```bash
cp .env.example .env
```

可配置的环境变量：

```env
VITE_API_BASE_URL=/api
VITE_APP_TITLE=Frontend Demo
```

## 🌐 API 代理

开发环境已配置 API 代理，会自动将 `/api` 开头的请求转发到 `http://localhost:8080`。

例如：
- 前端请求: `http://localhost:3000/api/user/1`
- 实际转发: `http://localhost:8080/user/1`

## 📝 示例代码

### 使用 Element Plus 组件

```vue
<template>
  <el-button type="primary" @click="handleClick">
    <el-icon><Plus /></el-icon>
    点击我
  </el-button>
</template>

<script setup>
import { ElMessage } from 'element-plus'

const handleClick = () => {
  ElMessage.success('点击成功！')
}
</script>
```

### 使用 Pinia Store

```vue
<script setup>
import { useCounterStore } from '@/stores/counter'

const counter = useCounterStore()

// 使用 state
console.log(counter.count)

// 使用 getter
console.log(counter.doubleCount)

// 调用 action
counter.increment()
</script>
```

### 调用 API

```vue
<script setup>
import { getUserInfo } from '@/api/user'

const fetchUser = async () => {
  try {
    const data = await getUserInfo(1)
    console.log(data)
  } catch (error) {
    console.error(error)
  }
}
</script>
```

## 🎯 与后端模块集成

此前端模块可以与以下后端模块配合使用：

1. **spring-ai-demo**: Spring AI 后端服务
2. **agent-scope-demo**: AgentScope 后端服务

启动后端服务后，前端会自动代理 API 请求到后端。

## 📚 参考文档

- [Vue 3 文档](https://cn.vuejs.org/)
- [Element Plus 文档](https://element-plus.org/zh-CN/)
- [Vite 文档](https://cn.vitejs.dev/)
- [Pinia 文档](https://pinia.vuejs.org/zh/)
- [Vue Router 文档](https://router.vuejs.org/zh/)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT
