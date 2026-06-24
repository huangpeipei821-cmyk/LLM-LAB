# LLM Lab

基于 Spring Boot + React 的 LLM API 实验平台。支持对接任意 OpenAI 兼容接口，集成 Function Calling、SSE 流式对话、多模型对比、结构化 JSON 输出、Embedding 相似度计算等功能。前后端分离，开箱即用。

## 功能

| 功能 | 说明 |
|------|------|
| 统一 API 代理 | 前端只需提供 API Key + Base URL，即可对接任意 OpenAI 兼容接口 |
| Function Calling | 内置天气查询、足球比赛预测（俱乐部&国家队）、当前时间、网页搜索等工具 |
| SSE 流式对话 | 完整支持流式响应中的工具调用链路，可保存多轮对话历史 |
| 模型对比 | 同一问题并发发给两个模型，对比响应速度与质量 |
| 结构化输出 | 强制 JSON 输出，大模型返回非法 JSON 时自动重试（最多 3 次） |
| Embedding 相似度 | 两段文本的语义相似度计算 |
| 对话管理 | 多轮对话的创建、保存与回溯 |
| 调用记录 | 自动记录每次 API 调用的模型、耗时、Token 用量、成功/失败状态 |

## 技术栈

- **后端**：Spring Boot 3.3.5 / WebFlux / JPA / H2
- **前端**：React 19 / Vite / TypeScript
- **数据源**：和风天气 / football-data.org / api-football.com / DuckDuckGo

## 项目结构

```
├── src/main/java/org/example/api_playground/llm
│   ├── client/          # LLM API 客户端（OpenAI 兼容）
│   ├── config/          # CORS、WebClient、DNS 配置
│   ├── controller/      # REST API（/agent/*）
│   ├── model/           # 数据模型与 DTO
│   ├── repository/      # JPA Repository
│   └── service/         # 核心业务逻辑
├── src/main/resources/
│   └── application.properties.example   # 配置文件模板
├── frontend/            # React 前端
└── pom.xml
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/agent/tools` | 获取可用工具列表 |
| `POST` | `/agent/chat` | 普通对话（支持 Function Calling） |
| `POST` | `/agent/stream` | SSE 流式对话 |
| `POST` | `/agent/compare` | 模型对比 |
| `POST` | `/agent/structured-output` | 结构化 JSON 输出 |
| `POST` | `/agent/embedding/similarity` | Embedding 相似度计算 |
| `GET` | `/agent/records` | 查询调用记录 |
| `GET` | `/agent/conversations` | 对话列表 |
| `POST` | `/agent/conversation` | 新建对话 |
| `DELETE` | `/agent/conversation/{id}` | 删除对话 |
| `GET` | `/agent/conversation/{id}/messages` | 获取对话消息 |

## 快速启动

### 环境要求

- JDK 17+
- Node.js 18+

### 后端

```bash
# 1. 复制并修改配置文件
cp src/main/resources/application.properties.example src/main/resources/application.properties
# 填写你自己的 API Key（天气、足球数据等，不填则使用 mock 数据）

# 2. 启动后端
./mvnw spring-boot:run
# 后端运行在 http://localhost:8080
```

### 前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
# 前端运行在 http://localhost:5173
```

### 使用

1. 浏览器打开 `http://localhost:5173`
2. 填入你的 LLM API Key 和 Base URL（如 `https://api.openai.com/v1`）
3. 选择模式（通用助手 / 天气专家 / 足球专家），开始对话

## 配置说明

`application.properties` 中的 API Key 说明：

| 配置项 | 用途 | 获取地址 |
|--------|------|----------|
| `weather.qweather.key` | 和风天气实时数据 | https://dev.qweather.com/ |
| `football.data.api.key` | 俱乐部足球数据 | https://www.football-data.org/ |
| `api-football.key` | 国家队足球数据 | https://www.api-football.com/ |
| `odds.api.key` | 赔率数据 | https://the-odds-api.com/ |

所有 Key 均为可选，未配置时自动降级为 mock 数据。
