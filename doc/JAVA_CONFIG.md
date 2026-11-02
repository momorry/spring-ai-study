# ⚠️ 重要：Java 版本配置说明

## 问题说明

项目拆分已完成，但需要配置正确的 Java 版本才能编译。

### 当前环境情况
- **项目要求**: Java 21
- **系统 Java**: Java 21 (已安装)
- **Maven 使用的 Java**: Java 1.8 (需要修改)

## 解决方案

### 方案 1：设置 JAVA_HOME 环境变量（推荐）

临时设置（仅当前命令行窗口）：

**PowerShell:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
mvn clean install
```

**CMD:**
```cmd
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10
mvn clean install
```

### 方案 2：永久设置 JAVA_HOME

1. 右键"此电脑" -> "属性" -> "高级系统设置" -> "环境变量"
2. 在"系统变量"中找到 `JAVA_HOME`
3. 修改值为 Java 21 的安装路径：`C:\Program Files\Java\jdk-21.0.10`
4. 重启命令行窗口或 IDE

### 方案 3：在 Maven 中指定 Java 版本

编辑 Maven 的 `settings.xml` 文件（通常位于 `~/.m2/settings.xml`），添加：

```xml
<profiles>
  <profile>
    <id>java-21</id>
    <properties>
      <java.home>C:\Program Files\Java\jdk-21.0.10</java.home>
    </properties>
  </profile>
</profiles>

<activeProfiles>
  <activeProfile>java-21</activeProfile>
</activeProfiles>
```

## 验证配置

配置完成后，运行以下命令验证：

```bash
mvn -version
```

应该看到：
```
Java version: 21.0.10, vendor: Oracle Corporation
```

## 编译项目

### 编译整个项目
```bash
mvn clean install
```

### 单独编译 Spring AI 模块
```bash
mvn clean install -pl spring-ai-demo
```

### 单独编译 AgentScope 模块
```bash
mvn clean install -pl agent-scope-demo
```

## IDE 配置

### IntelliJ IDEA

1. **项目结构设置**:
   - File -> Project Structure -> Project
   - Project SDK: 选择 Java 21
   - Project language level: 21

2. **Maven 设置**:
   - File -> Settings -> Build, Execution, Deployment -> Build Tools -> Maven
   - JRE: 选择 Java 21

3. **重新导入 Maven 项目**:
   - 右键 pom.xml -> Maven -> Reload Project

### Eclipse

1. **Window -> Preferences -> Java -> Installed JREs**
2. **添加 Java 21**
3. **右键项目 -> Maven -> Update Project**

## 常见问题

### Q1: 我没有安装 Java 21 怎么办？

**A1**: 从 Oracle 官网下载并安装 Java 21 JDK：
https://www.oracle.com/java/technologies/downloads/#java21

或者使用 OpenJDK：
https://adoptium.net/

### Q2: 我想使用 Java 17 可以吗？

**A2**: 理论上可以，但需要修改 pom.xml 中的 `java.version` 属性为 17，并且可能需要调整一些依赖版本。

### Q3: 为什么 Maven 使用的是 Java 8？

**A3**: 因为系统的 JAVA_HOME 环境变量指向了 Java 8。Maven 默认使用 JAVA_HOME 指定的 Java 版本。

## 项目结构确认

项目已成功拆分为以下结构：

```
spring-ai-embabel/
├── pom.xml                      # 父模块
├── spring-ai-demo/              # Spring AI 模块
│   ├── pom.xml
│   └── src/main/java/com/moli/spring/ai/
└── agent-scope-demo/            # AgentScope 模块
    ├── pom.xml
    └── src/main/java/com/moli/agent/scope/
```

两个模块完全独立，互不影响。

## 下一步

1. 配置好 Java 21 环境
2. 运行 `mvn clean install` 编译项目
3. 在 IDE 中重新导入 Maven 项目
4. 开始开发！
