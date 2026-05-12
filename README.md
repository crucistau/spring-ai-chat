# Spring AI Chat

基于 **Spring Boot 3.4 + Spring AI** 大模型的前后端分离 AI 对话应用。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.4.5 |
| AI 集成 | Spring AI (spring-ai-starter-model-deepseek) | 1.0.0 |
| 大模型 | DeepSeek (deepseek-chat) | - |
| 持久层 | MyBatis-Plus | 3.5.9 |
| 数据库 | MySQL | 8.0+ |
| 前端框架 | Vue 3 | 3.5.x |
| 构建工具 | Vite | 6.x |
| HTTP 客户端 | Axios | 1.9.x |
| Markdown 渲染 | markdown-it + highlight.js | - |
| JDK | OpenJDK | 17+ |

## 项目结构

```
spring-ai-chat/
├── server/                                    # 后端 (Spring Boot)
│   ├── pom.xml                                # Maven 配置
│   └── src/main/
│       ├── java/com/example/chat/
│       │   ├── ChatApplication.java           # 启动类
│       │   ├── config/
│       │   │   └── CorsConfig.java            # 跨域配置
│       │   ├── controller/
│       │   │   └── ChatController.java        # REST 接口
│       │   ├── dto/
│       │   │   ├── ChatRequest.java           # 请求体
│       │   │   ├── ChatResponse.java          # 响应体
│       │   │   ├── ConversationResponse.java  # 会话摘要
│       │   │   ├── ChatMessageResponse.java   # 消息记录
│       │   │   └── RenameRequest.java         # 重命名请求
│       │   ├── entity/
│       │   │   ├── Conversation.java          # 会话实体
│       │   │   └── ChatMessage.java           # 消息实体
│       │   ├── mapper/
│       │   │   ├── ConversationMapper.java    # 会话 Mapper
│       │   │   └── ChatMessageMapper.java     # 消息 Mapper
│       │   └── service/
│       │       ├── ChatService.java           # 服务接口
│       │       └── impl/
│       │           └── ChatServiceImpl.java   # 服务实现
│       └── resources/
│           ├── application.yml                # 应用配置
│           ├── db/
│           │   └── schema.sql                 # 数据库建表脚本
│           └── prompts/
│               └── system-prompt.st           # 系统提示词
│
└── web/                                       # 前端 (Vue 3 + Vite)
    ├── package.json
    ├── vite.config.js                         # Vite 配置（含 API 代理）
    ├── index.html
    └── src/
        ├── App.vue                            # 根组件（含侧边栏）
        ├── main.js                            # 入口
        ├── api/
        │   └── chat.js                        # API 调用（含 SSE 流式 + 会话管理）
        ├── components/
        │   ├── ChatWindow.vue                 # 聊天窗口
        │   ├── ChatInput.vue                  # 输入框
        │   ├── MessageBubble.vue              # 消息气泡
        │   └── ConversationSidebar.vue        # 会话列表侧边栏
        └── assets/styles/
            └── main.css                       # 全局样式
```

## 功能特性

- **多轮对话** — 基于 conversationId 维护会话上下文
- **流式响应 (SSE)** — 后端 `Flux<String>` 逐字输出，前端实时渲染
- **会话持久化** — 聊天记录存储到 MySQL，服务重启不丢失
- **会话管理** — 新建对话、切换对话、重命名、删除
- **历史加载** — 点击侧边栏即可恢复完整对话记录
- **Markdown 渲染** — 支持代码高亮、表格、列表等富文本
- **前后端分离** — 后端 8080 端口，前端 5173 端口，Vite 代理转发 API

## API 接口

### 同步对话

```
POST /api/chat
Content-Type: application/json

请求体:
{
  "message": "你好",
  "conversationId": "可选，会话ID"
}

响应体:
{
  "content": "你好！有什么可以帮你的吗？",
  "conversationId": "uuid",
  "model": "deepseek-chat"
}
```

### 流式对话

```
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream

请求体:
{
  "message": "你好",
  "conversationId": "可选，会话ID"
}

响应: SSE 事件流，逐块返回文本内容
```

### 会话管理

```
GET    /api/conversations                      # 获取会话列表（按更新时间倒序）
GET    /api/conversations/{id}/messages        # 获取会话的所有消息
DELETE /api/conversations/{id}                 # 删除会话及其消息
PUT    /api/conversations/{id}/title           # 重命名会话
```

## 数据库设计

### conversation 表（会话）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | 会话 ID (UUID) |
| title | VARCHAR(200) | 会话标题（取首条消息前 50 字） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间（自动维护） |

### chat_message 表（消息）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO | 消息 ID |
| conversation_id | VARCHAR(36) | 关联会话 ID（索引） |
| role | VARCHAR(20) | 角色：user / assistant |
| content | TEXT | 消息内容 |
| created_at | DATETIME | 创建时间 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm 9+
- MySQL 8.0+
- DeepSeek API Key（[获取地址](https://platform.deepseek.com/api_keys)）

### 1. 初始化数据库

```bash
mysql -u root -p < server/src/main/resources/db/schema.sql
```

该脚本会自动创建 `chatdb` 数据库及 `conversation`、`chat_message` 两张表。

### 2. 配置数据库连接

编辑 `server/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/chatdb?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: your-password
```

也可通过环境变量覆盖：

```bash
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your-password
```

### 3. 配置 API Key

方式一：环境变量（推荐）

```bash
# Linux / macOS
export DEEPSEEK_API_KEY=sk-your-key-here

# Windows PowerShell
$env:DEEPSEEK_API_KEY="sk-your-key-here"

# Windows CMD
set DEEPSEEK_API_KEY=sk-your-key-here
```

方式二：直接修改配置文件

编辑 `server/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    deepseek:
      api-key: sk-your-key-here
```

### 4. 启动后端

```bash
cd server
mvn spring-boot:run
```

后端启动在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd web
npm install
npm run dev
```

前端启动在 `http://localhost:5173`，打开浏览器访问即可。

## 配置说明

后端核心配置项（`application.yml`）：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.ai.deepseek.api-key` | DeepSeek API 密钥 | - |
| `spring.ai.deepseek.chat.options.model` | 模型名称 | deepseek-chat |
| `spring.ai.deepseek.chat.options.temperature` | 生成温度 (0-2) | 0.7 |
| `spring.ai.deepseek.chat.options.max-tokens` | 最大生成 token 数 | 2048 |
| `spring.datasource.url` | MySQL 连接地址 | localhost:3306/chatdb |
| `spring.datasource.username` | 数据库用户名 | root |
| `spring.datasource.password` | 数据库密码 | root |
| `mybatis-plus.configuration.map-underscore-to-camel-case` | 驼峰命名映射 | true |

可选模型：`deepseek-chat`（通用对话）、`deepseek-reasoner`（深度推理）。

## 构建部署

### 后端打包

```bash
cd server
mvn clean package -DskipTests
java -jar target/spring-ai-chat-1.0.0.jar
```

### 前端打包

```bash
cd web
npm run build
```

产物输出到 `web/dist/` 目录，可部署到 Nginx 或其他静态服务器。

## 参考文档

- [Spring AI DeepSeek 官方文档](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html)
- [Spring AI 中文文档](https://springdoc.cn/spring-ai/index.html)
- [DeepSeek API 开放平台](https://platform.deepseek.com/)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Baeldung - Spring AI DeepSeek 教程](https://www.baeldung.com/spring-ai-deepseek-cot)
