# 🚀 AgentScope Demo 启动指南

## ✅ 启动类已创建

**启动类位置：**
```
agent-scope-demo/src/main/java/com/moli/agent/scope/AgentScopeApplication.java
```

**包结构：**
```
com.moli.agent.scope
├── AgentScopeApplication.java  ⭐ 启动类
├── controller/
│   └── ChatController.java     # 聊天控制器
├── service/
│   └── ChatService.java        # 聊天服务
├── harness/
│   ├── agent/                  # Agent 配置
│   ├── middleware/             # 中间件
│   └── model/                  # 模型创建器
└── common/
    ├── base/                   # 基础类
    └── vo/                     # 值对象
```

---

## 🎯 启动方式

### 方式 1：使用 Maven 命令启动

```bash
# 进入 agent-scope-demo 目录
cd agent-scope-demo

# 编译项目
mvn clean compile

# 启动应用
mvn spring-boot:run
```

### 方式 2：使用 IDE 启动

1. 在 IntelliJ IDEA 中打开项目
2. 找到 `AgentScopeApplication.java` 文件
3. 右键点击 → Run 'AgentScopeApplication'

### 方式 3：打包后运行

```bash
# 打包项目
mvn clean package -DskipTests

# 运行 JAR 文件
java -jar target/agent-scope-demo-0.0.1-SNAPSHOT.jar
```

---

## 🔧 配置说明

### 应用配置文件

**位置：** `src/main/resources/application.yml`

```yaml
spring:
  profiles:
    active: local
  application:
    name: agent-scope-demo

server:
  port: 8081  # 建议与 spring-ai-demo 使用不同端口
```

**本地配置：** `src/main/resources/application-local.yml`

根据实际需求配置模型参数和其他设置。

---

## 🌐 可用接口

### 1. ReAct Agent 对话接口

**请求：**
```http
POST /reactagent/conversation
Content-Type: application/json

{
  "message": "你好，请介绍一下自己",
  "history": []
}
```

**响应：**
```
Flux<AgentEvent> - 流式响应
```

---

## 📋 前置要求

### Java 版本
- **要求**: Java 21 或更高版本
- **检查**: `java -version`

### Maven
- **要求**: Maven 3.6+
- **检查**: `mvn -version`

### 环境变量（可选）

如果需要配置模型 API，可以设置环境变量：

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_BASE_URL="http://your-api-endpoint"

# Linux/Mac
export OPENAI_API_KEY="your-api-key"
export OPENAI_BASE_URL="http://your-api-endpoint"
```

---

## 🔍 验证启动

启动成功后，你会看到类似以下的日志：

```
  ____                            ____  
 / ___|   __ _   _ __ ___    ___  / ___| 
 \___ \  / _` | | '_ ` _ \  / _ \| |     
  ___) || (_| | | | | | | ||  __/| |___  
 |____/  \__,_| |_| |_| |_| \___| \____| 
                                         
:: Spring Boot ::                (v3.5.4)

Starting AgentScopeApplication using Java 21.0.10
No active profile set, falling back to 1 default profile: "default"
Tomcat initialized with port(s): 8081 (http)
Starting Servlet engine: [Apache Tomcat/10.1.x]
Initialized JPA EntityManagerFactory for persistence unit 'default'
Started AgentScopeApplication in x.xxx seconds
```

### 测试接口

使用 curl 或 Postman 测试：

```bash
curl -X POST http://localhost:8081/reactagent/conversation \
  -H "Content-Type: application/json" \
  -d '{"message": "你好", "history": []}'
```

---

## ⚙️ 常见问题

### 1. 端口冲突

如果出现端口占用错误，修改 `application.yml`：

```yaml
server:
  port: 8082  # 改为其他端口
```

### 2. Java 版本不匹配

确保使用 Java 21：

```bash
# 检查当前 Java 版本
java -version

# 如果不是 21，设置 JAVA_HOME
export JAVA_HOME=/path/to/jdk-21
```

### 3. 依赖下载失败

```bash
# 清理并重新下载依赖
mvn clean install -U
```

### 4. Lombok 注解处理器未启用

在 IDE 中：
- **IntelliJ IDEA**: Settings → Plugins → 搜索 "Lombok" → 安装并启用
- **Eclipse**: 下载 lombok.jar 并运行安装

---

## 📦 项目依赖

### 核心依赖

- **Spring Boot WebFlux** - 响应式 Web 框架
- **AgentScope Harness** - AgentScope 核心框架
- **AgentScope Extensions Model OpenAI** - OpenAI 模型支持
- **Lombok** - 简化代码
- **Hutool** - Java 工具库

### 查看依赖树

```bash
mvn dependency:tree
```

---

## 🚀 下一步

1. **配置模型参数**
   - 编辑 `application-local.yml`
   - 设置 API Key 和 Base URL

2. **测试接口**
   - 启动应用
   - 使用 Postman 或 curl 测试 `/reactagent/conversation`

3. **查看代码**
   - `ChatController.java` - 控制器
   - `ChatService.java` - 业务逻辑
   - `ReActAgentConfig.java` - Agent 配置

4. **扩展功能**
   - 添加更多 Agent
   - 实现自定义中间件
   - 集成数据库

---

## 📝 开发建议

### 热重载

开发时可以使用 devtools 实现热重载：

```bash
mvn spring-boot:run
```

修改代码后会自动重启应用。

### 调试模式

使用调试模式启动：

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

然后在 IDE 中连接到 5005 端口进行调试。

### 日志级别

临时调整日志级别：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--logging.level.com.moli=DEBUG"
```

---

## 🎉 启动成功！

现在你已经成功创建了 AgentScope Demo 的启动类，可以通过上述方式启动应用了！

**默认访问地址：** http://localhost:8081

**API 端点：** `/reactagent/conversation`

祝你开发愉快！🚀
