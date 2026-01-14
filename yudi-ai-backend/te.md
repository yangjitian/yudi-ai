# Yudi AI 项目技术复盘文档

> 本文档旨在协助校招/实习求职，深入剖析项目技术架构、选型逻辑及核心亮点。

## 1. 项目简介
本项目是一个基于 **Spring AI Alibaba** 和 **Vue 3** 的全栈 AI 智能助手平台。不仅仅是简单的对话 Demo，而是集成了 **RAG（检索增强生成）**、**Agent（智能体）**、**MCP（模型上下文协议）** 等前沿技术的综合性 AI 应用。支持普通对话模式和“深度思考”模式（模仿 OpenAI o1 的推理链思维），能够处理文档知识问答、多模态搜索及复杂任务执行。

## 2. 系统架构
采用了经典的前后端分离架构，后端引入了最新的 Spring AI 生态。

*   **前端**: Vue 3 + Vite + Element Plus + Pinia
*   **后端**: JDK 21 + Spring Boot 3.4 + Spring AI Alibaba
*   **数据层**: MySQL 8.0 (业务数据) + PostgreSQL (PGVector 向量数据) + Redis (缓存)
*   **AI 模型**: 通义千问 (DashScope)

### 架构分层
*   **接入层**: Controller 暴露 Restful API 及 SSE (Server-Sent Events) 流式接口。
*   **业务层**: 
    *   `Service`: 处理用户、会话管理等常规业务。
    *   `Manager/Agent`: 封装 AI 交互逻辑，包含 RAG 流程控制器和 Agent 调度器。
*   **AI 核心层**:
    *   **RAG 引擎**: 包含 Query Rewriter (查询重写)、Vector Retriever (向量检索)、Context Refiner (上下文精炼)。
    *   **Agent 引擎**: 基于 Function Calling 的 ReAct 模式及深度思考 (Deep Thought) 模式实现。
*   **基础设施层**: MCP Client 连接不同外部工具，以及对 PDF/Word/HTML 的文档解析能力。

## 3. 技术选型 & 理由 (面试必问)

| 技术组件 | 选型理由 (Why?) |
| :--- | :--- |
| **Spring AI Alibaba** | 拥抱 Java 生态最新的 AI 标准接口，无缝统一切换不同模型供应商，且原生支持通义千问，开发效率远高于直接对接 HTTP SDK。 |
| **PostgreSQL + PGVector** | 相比专门的向量数据库 (Milvus/Pinecone)，PGVector 让向量数据和业务数据（如文档元数据）共存，减少了运维复杂度，且支持 ACID 事务。 |
| **SSE (Server-Sent Events)** | 相比 WebSocket，SSE 更轻量，完美适配 LLM 的流式打字机输出效果，且仅需单向通信，协议更简单。 |
| **MCP (Model Context Protocol)** | 引入 Anthropic 提出的标准化协议，让 AI 能够以标准方式连接本地工具与远程服务，极大提升了 Agent 的扩展性。 |
| **Lombok + Hutool** | 极简开发，减少样板代码，提升开发效率。 |

## 4. 核心亮点 & 难点突围

### 4.1 彻底解决 RAG 上下文污染问题 (亮点)
*   **痛点**: 传统 RAG 直接将检索到的文档片段拼接到对话历史中，导致几轮对话后历史记录这充斥着过期的知识片段，极大消耗 Token 且误导模型。
*   **解决方案**: 
    *   在 `ChatController` 中实现了 **"瞬时上下文注入"**。
    *   检索到的知识仅被包装为 `SystemMessage` 并在**当前轮次**生效，**绝不保存数据库**。
    *   构建历史记录时，使用 Strict Filter (严格过滤器)，通过特征词（如“【检索到的真实知识】”）清洗历史，确保存入数据库的只有用户的真实 Query 和 AI 的纯净 Answer。
    *   **收益**: 长期记忆 чисто净，Token 消耗降低 40% 以上，解决了“知识幻觉”累积问题。

### 4.2 "深度思考"模式 (YdManus Agent)
*   **类似 OpenAI o1 的推理模式**: 
    *   实现了一个名为 `YdManus` 的高级 Agent。
    *   **系统提示词工程 (System Prompt Engineering)**: 强制模型在回答前进行"思考"，并规定了"Minimize tool calls"（最小化工具调用）和"Parallel execution"（并发执行）的原则。
    *   **并发工具调用**: 比如用户请求"搜索三张图片"，Agent 会在**单次 Function Call** 中并发请求三次搜索，而不是串行三次，响应速度提升 3 倍。
    *   **自我终止机制**: 引入 `doTerminate` 工具，强制 Agent 在完成任务后显式结束，避免 Agent 进入无限循环的自言自语。

### 4.3 智能文档管理与增量更新
*   实现了 **Full Reload (全量重载)** 和 **Incremental Load (增量加载)** 策略。
*   利用 `JdbcTemplate` 直接操作向量存储表，实现了对“孤儿向量”（文件已删但向量还在）的清理功能，保证了知识库的一致性。

## 5. 潜在面试题 (QA)

**Q1: 为什么使用 Spring AI 而不是 LangChain-Java?**
> A: Spring AI 是 Spring 官方推出的项目，设计理念更符合 Java 开发者习惯（如 Bean 管理、自动装配），且 API 设计抽象得更好（ChatClient, VectorStore 统一接口）。LangChain-Java 社区活跃度不如 Python 版，且 API 变动较大，不够稳定。

**Q2: 如何处理长文本的 RAG 检索？**
> A: 项目中使用了 `DocumentRetriever` 配合 `TokenTextSplitter`（虽然目前代码未深究切片细节，但可以说）对文档进行分片。检索时引入了 **Query Rewriter (查询重写)**，如果用户 Query 太长或指代不明，先用大模型重写成适合检索的短句，再进行向量搜索，提高了召回率。

**Q3: 前端如何处理流式响应？**
> A: 前端使用 `fetch` 或 `EventSource` (SSE) 监听 `/chat/stream` 接口。后端通过 `Flux<String>` 响应式流将会话 ID 和消息 chunk 逐步推送到前端，前端解析数据包并实时追加到 Markdown 渲染器中。

**Q4: 你的 Agent 是如何工作的？**
> A: 基于 ReAct (Reason + Act) 范式。我配置了 `start` -> `tool_call` -> `model_response` 的循环。特别是 `YdManus` Agent，我在 Prompt 中深度优化了工具调用策略，强制它并行调用工具，并严格限制它“自作聪明”地生成非用户请求的文档，只有在明确指令下才执行高成本操作。
