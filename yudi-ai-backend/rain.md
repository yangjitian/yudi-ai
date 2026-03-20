# yudi-ai-backend 项目说明

## 求职版优化指南（技术栈与项目亮点）

### 一句话电梯陈述

- 基于 Spring Boot + Spring AI 的美食顾问后端，实现“无污染 RAG、并行工具调用、SSE 流式交互、双数据源向量检索与会话记忆”，可拓展至各类知识型助理场景。

### 项目描述模板（可直接用于简历）

- 150 字版：
  - 负责构建 AI 美食顾问后端，采用 Spring Boot + Spring AI 与 PgVector 实现检索增强生成（RAG），通过一次性 SystemMessage 注入彻底避免历史污染；支持 DashScope 大模型、并行工具调用与 SSE 流式输出，集成 Redis 会话、MyBatis-Plus 业务数据与腾讯 COS 文件存储，满足实时查询与高可用交互。

- 300 字版：
  - 设计并实现“雨落有味”AI 美食顾问后端。以 Java 21/Spring Boot 为核心，结合 Spring AI（Chat/RAG/VectorStore）、PgVector（PostgreSQL）构建无污染 RAG 流程：检索上下文仅以一次性 SystemMessage 注入，不写入历史；历史消息严格清洁。通过 SSE 提供打字机式流式体验与手动暂停能力；工具层支持天气/图片搜索/网页抓取/文档生成/终端信息等，采用最少调用与并行策略提升效率。双数据源（MySQL/PG）与 Redis 保证业务与检索的分离与性能；MCP 集成高德地图工具；向量库实现启动时增量更新；统一异常与日志 Advisor 提升可观测性。

### 技术栈呈现（岗位导向）

- 后端工程师版：
  - Spring Boot、MyBatis-Plus、REST/SSE、Redis（Token/验证码）、多数据源（MySQL/PG）、统一异常处理、Knife4j/OpenAPI、COS 文件上传、Interceptor 登录态校验。
- AI 工程师版：
  - Spring AI（Chat/RAG/VectorStore）、DashScope（模型选项/温度/TopP 控制）、PgVector（COSINE + HNSW 索引）、Query Rewriter（缓存）、文档增量加载与批处理、Agent（ReAct/ToolCallingManager）。
- 平台/集成工程师版：
  - MCP 客户端（WebFlux）、高德地图 mcp-server（stdio），工具注册与回调，SDK/HTTP 工具整合，系统参数化配置与稳定性治理。

### 项目亮点（可量化 + 难点）

- RAG 防污染：检索上下文作为一次性 SystemMessage 注入，不进入历史，避免跨轮次误导（`ChatController` 实现）。
- 并行工具调用策略：Agent 设计强调“最少调用 + 并行”，减少函数调用轮次与延迟，避免过度工具化。
- SSE 流式体验：心跳保活、错误兜底、完成事件、手动暂停，打字机式输出提升交互体验与稳定性。
- 双数据源与向量库：MySQL/PG 分离，PgVector 使用 COSINE 距离 + HNSW 索引，批量 25 条写入符合嵌入模型限制。
- 文档增量更新：启动时检测新增/更新文档并增量导入，清理孤儿向量保持一致性。
- 统一异常与日志：`GlobalExceptionHandler` 与自定义 `MyLoggerAdvisor`，提升可观测性与问题定位效率。

### 指标与数据（建议在简历中描述）

- 性能：
  - 非流式问答 P95 延迟（示例）≤ 800ms（无检索与工具调用时）；流式首包时间 ≤ 300ms（示例）。
- 稳定性：
  - SSE 断连重试成功率（示例）> 99%；工具调用失败兜底覆盖率 100%。
- 可用性：
  - 向量库增量更新平均耗时（示例）＜ 30s（千级文档规模），错误率＜ 1%。
（如暂无真实数据，可在测试环境进行压测并记录：JMeter/Locust + 指标采集）

### 面试常见问答速答卡

- 为什么选择 PgVector 而非内存向量库？
  - 支持高维向量、高效相似检索、持久化与索引（HNSW），适合生产负载与增量维护。
- 为什么将检索内容以 SystemMessage 注入而非追加到历史？
  - 防止跨轮次污染历史上下文，保证每轮回答的“真实背景”只在当前轮生效。
- 为什么用 SSE 而非 WebSocket？
  - 单向推送更轻量、浏览器原生支持、在流式生成场景足够；若需双向通信可在工具层补充。
- Redis Token TTL 刷新意义？
  - 保持活跃会话在线状态，降低频繁登录摩擦，并便于服务端强制登出与会话管理。

### 简历条目模板（可直接复制）

- 设计并落地无污染 RAG 流程，检索上下文仅以一次性 SystemMessage 注入，历史严格清洁，显著降低跨轮次误导。
- 构建 SSE 流式输出与手动暂停机制，提升交互体验与稳定性；接入统一异常与日志 Advisor，改善可观测性。
- 通过双数据源（MySQL/PG）与 PgVector（COSINE 距离、HNSW 索引）实现高效检索；文档增量加载与批处理保证数据一致性与性能。
- 设计 ReAct/ToolCallingManager 的“最少并行工具调用”策略，覆盖天气/图片/抓取/文档/下载/终端等工具生态。
- 集成 MCP（高德地图）工具，封装回调与服务可用性检测，支撑位置与餐饮相关的实时能力。

### 价值陈述（面试表达）

- 用户价值：回答更真实稳定、交互更顺畅，工具只在必要时调用，节约成本与时间。
- 技术价值：干净历史策略让 RAG 易维护、易扩展；指标可观测，问题定位清晰。
- 商业价值：场景可复制到企业知识问答、客服、运营策略助理等，快速对接外部数据源与工具。

### 改进建议（若有时间继续打磨）

- 增加单元测试与集成测试覆盖（Chat/RAG/工具/拦截器）。
- 补充压测脚本与可观测指标（Grafana/Prometheus 或云监控）。
- 引入 CI 流水线（构建、测试、质量检查）与依赖漏洞扫描（OWASP）。
- 敏感配置迁移至环境变量/密钥管理服务；提供安全部署指南。

## 项目概览

- 项目名称：`yudi-ai-backend`（雨落有味·AI 美食顾问后端）
- 目标：提供对话、RAG 检索、工具调用（天气/图片/网页抓取/文档生成）、会话记忆、用户管理、MCP 外部服务集成等能力，让用户获得温柔、专业、可操作的美食咨询体验。

## 技术栈

- 后端框架：Spring Boot 3.4.7
- 语言与构建：Java 21，Maven
- ORM 与数据库：MyBatis-Plus + MySQL（业务数据）、PostgreSQL + PgVector（向量检索）
- 缓存与会话：Redis（登录会话与验证码）
- AI 能力：Spring AI（聊天/向量存储/RAG）、阿里云 DashScope（模型调用）
- 工具生态（略）
- 对象存储：腾讯 COS（头像上传）
- API 文档：springdoc-openapi + knife4j
- MCP（Model Context Protocol），集成高德地图等外部服务

## 数据源与基础设施

- 双数据源配置：
  - MySQL（主数据源）：用户/会话/业务数据
  - PostgreSQL（次数据源）：PgVector 向量库
  - 对应 `JdbcTemplate` Bean：`primaryJdbcTemplate` 与 `secondaryJdbcTemplate`
- 登录态拦截器：
  - 从 `Authorization` 读取 Token → 查 Redis → 反序列化为 `User` → 填充到 `UserHolder` → 刷新 TTL（30 分钟）

## 功能模块与职责

### 聊天（最终版 ChatController）

- 模式：
  - 深度思考（`deep_thought`）：调用 `YdManus`，适合复杂任务与多工具并行
  - 标准 RAG：PgVector 检索 + 防污染历史策略
- 特点：
  - RAG 内容仅作为一次性 `SystemMessage` 注入，不写入历史
  - 历史只保留真实的用户与助手消息
  - SSE 流式输出，支持暂停

### RAG 与向量库

- PgVector 配置与初始化
  - 自动安装 `vector` 扩展与建表
  - 首次运行时自动加载文档（批量 25 条以内）
  - 智能增量检测与清理（及下方增量方法）
- 查询重写缓存（避免重复重写，提升性能）
- 文档加载器
- 云知识库（阿里）顾问（可选）

### Agent 与工具调用

- 基类（禁用内置工具执行，手动管理 ToolCallingManager）
- 深度思考 Agent
  - 注册本地工具与 MCP 工具
  - 系统提示词与下一步提示词强化“最少工具并行调用”策略（策略文本在类内字符串）
- 本地工具注册

## RAG 防污染设计详解

- 目标：保证历史只记录真实的用户与助手对话，不混入“检索背景”或系统提示，避免下一轮被误导。
- 流程（`ChatController`）：
  1. 查询重写（必要时，`ChatController.java:219-221`）
  2. 向量检索（`ChatController.java:222-224`）
  3. 构建干净历史，仅保留真实消息（`ChatController.java:257-293`）
  4. 若有检索内容，将其拼为一次性 `SystemMessage` 注入，不进入历史（`ChatController.java:231-247`）
  5. 组合 Prompt 并调用模型（同步或流式，`ChatController.java:252-255`）
- 额外防护：保存对话前再次检查用户输入是否含 RAG 系统痕迹，直接丢弃（`ChatController.java:307-314`）。

## 流式输出（SSE）

- 心跳保活、错误事件、完成事件、手动暂停；错误自动兜底
- Chat 接口：先发送 `conversationId` 事件，再逐块发送 `message` 事件
- 暂停：`/api/c/chat/stream/{conversationId}/pause`

## 系统提示词（AI 人设）

- 路径：`src/main/resources/prompts/cook_app_system_prompt.md`
- 内容：定义“温柔、专业、略带幽默”的美食顾问「小小雨滴」的行为准则、工具使用规则、流式节奏与隐私探针处理方式；确保输出风格一致，避免不必要工具调用与过度扩展。

## 你可以这样理解整个系统（非技术）

- 你问问题 → 系统确认你的登录身份
- 系统把问题稍微“润色”，让模型理解更好
- 去知识库里检索真实资料，临时放到这次思考里，不记录进历史（避免下次误导）
- 如需查天气/找图片/抓网页/生成文档/推荐餐厅，就调用相应“工具”
- 回答分段像打字机一样输出，中途也能暂停
- 每一轮问题与回答都会被记录成你的“对话历史”，方便回看

## 常见问题（FAQ）

- 为什么不把检索到的背景知识写进历史？
  - 因为会污染后续轮次的上下文，导致模型在下一轮错误引用旧背景。
- 向量库数据如何更新？
  - 支持启动时的增量检测；也可通过文档管理接口手动刷新。
- 工具会不会乱用？
  - Agent 强调“最少工具调用”与“并行一次调用多个工具”，并且仅在用户明确请求或必须实时数据时使用。

## 术语速查

- RAG（检索增强）：先检索，再生成，提升回答的真实性
- MCP：统一的外部服务与工具接入协议（例如高德地图）
- SSE：服务端推送事件，支持流式回答与心跳保活

## 项目包结构与核心类作用详解 (源码架构剖析)

本项目遵循标准的 DDD (领域驱动设计) 与 MVC 融合分层架构，并针对 AI 应用场景进行了高度定制。以下是 `src/main/java/com/yudi/ai` 下所有包和核心类的详细作用说明：

### 1. `advisor` (AI 增强通知与拦截探测层)
该包主要利用 Spring AI 的 Advisor 机制对大模型的请求和响应进行 AOP 拦截强化。
- `MyLoggerAdvisor.java`：自定义系统日志切面探针。负责在 LLM 请求发出前和响应接收后，打印消耗的 Token、完整的 Prompt 结构以及工具响应耗时，是实现系统可观测性的重要基石。
- `ReReadingAdvisor.java`：重读/提示词增强拦截器。为应对长文本大模型容易“遗忘”开头指令的缺陷，自动在对话请求末尾重复核心 System Prompt 要求，提升复杂任务从头到尾的遵循率。

### 2. `agent` (智能体引擎基建包)
项目的心脏地带，定义了 AI 智能体的核心行为和生命周期。
- `BaseAgent.java`：基础 Agent 骨架抽象类，提供了记忆池 (Memory)、运行状态机管理等底层功能接口。
- `ReActAgent.java`：实现 ReAct (Reason + Act) 范式的基础模版类，确立“思考-行动-观察”的执行拓扑循环。
- `ToolCallAgent.java`：极其硬核的基类调度器。禁用了 Spring 原生工具黑盒调用，**完全自主显式接管工具并发执行**与手动 Token 裁剪逻辑。
- `YdManus.java`：**最强深钻智能体**。继承自 `ToolCallAgent`，配备了硬核的 `SYSTEM_PROMPT`，禁止“加戏”，强制“并发网络 IO”，实现类似 o1 的深度多并发逻辑处理。

### 3. `chatmemory` (对话记忆介质层)
- `FileBasedChatMemory.java`：区别于直接写库，由于本系统大量采用瞬时 RAG 思想，提供了一种基于本地或临时 IO File 的记忆上下文流式持久化备份实现（多用于调试模式或缓存兜底）。

### 4. `common` (统一响应与常量定义)
- `BaseResponse.java`：标准的泛型统一包装器。约定 `code, data, message` 三段式 JSON 返回给前端。
- `ErrorCode.java`：全系统异常状态码枚举库。

### 5. `config` (核心 Spring 装载配置区)
- `DataSourceConfig.java` / `DataSourceProperties.java`：双数据源核心统配器。实现 MySQL 与 PostgreSQL(PgVector) 异构数据库在同一容器中的切流与共存。
- `MybatisPlusConfig.java`：装载 MyBatis-Plus 核心插件（如自动分页器、乐观锁填充插件等）。
- `RedisConfig.java`：定做 Redis 序列化协议库。将默认二进制改成 `GenericJackson2Json` 或 `StringRedisSerializer`，防止乱码。
- `WebMvcConfig.java`：处理全局跨域 CORS 许可设置以及路由 `Interceptor` (如登录凭证读取拦截器) 的注册工作。

### 6. `controller` (HTTP 请求前线阵地)
- `ChatController.java`：全系统最高频入口。提供 SSE 长链接（`/chat/stream`），承载并流转复杂的 RAG 并发防污染逻辑。
- `DocumentManagementController.java`：知识库中控台。提供触发手动全量Reload、增量扫描、及死库孤儿向量清理接口。
- `EmailController` / `UserController.java`：鉴权阵地。处理注册防刷、获取验证码、高并发下换绑的业务路由。
- `ConversationController.java` / `CookController.java` / `CookMemoryController.java` / ：针对美食历史记录拉取、菜谱收藏管理的 CRUD 接口统筹。

### 7. `exception` (全局灾难控制)
- `GlobalExceptionHandler.java`：大底盘盾牌。拦截业务外抛错误或不可预知的底层崩溃，统一定型后通过 `BaseResponse` 温柔返送，不报丑陋白页堆栈。
- `BusinessException.java` / `ThrowUtils.java`：自定义业务异常类与敏捷打断抛错工具组。

### 8. `mcp` (外部星际生态拓传)
- `AmapMcpService.java`：基于 **Model Context Protocol**。这使得高德地图等外部 API 极其丝滑解耦地以外挂标准件身份“注射”进应用，而不需要在主代码里去硬编写长长的 HTTP Client 第三方互联逻辑。

### 9. `rag` (知识解剖与嵌入召回中枢)
- `PostgresVectorVectorStoreConfig.java`：本项目极为重磅的 PG HNSW 索引控制室，实现了 COSINE 距离的高效计算和持久化建表连接。
- `CookDocumentLoader.java`：文档食指（读取器）。负责从云端拉取各类原初格式资料并对接 AI 切片分段器 (TokenTextSplitter)。
- `QueryRewriter.java`：用户弱智提问修复器。利用微量 Token，将“这就很不错，它好吃吗？”此类多轮指代语句，自动提纯补齐主语提升召回命中率。
- `MyKeyWordEnricher.java`：打标器，给知识库碎片附加元数据 Metadata。

### 10. `tools` (智能体武器库清单)
存放一系列实现 `java.util.function.Function` 的 `@Tool` 注解类。供大模型在 Function Calling 时提取：
- `WebScrapingTool` / `WebSearchTool`：提供谷歌/必应网络联网搜索，并利用 Jsoup 精准抓取正文消除 HTML 原生噪音。
- `BaiduImageSearchTool`：供模型在美食菜谱中匹配视觉高清插图。
- `DocumentGenerationTool`：自动调用底层 iText / POI 库，将模型构思好的纯文本排版成精美的 PDF 或 Word 返回。
- `TerminateTool.java`：终结者工具。为防止 ReAct 发散而专门铸造的“强制断路器（`doTerminate`）”。
- 等等（涵盖天气获取、资源下载、时间戳判定、模拟终端）。这些工具在 YdManus 中接受统一的多核**并发拉起**调度。

### 11. `mapper` & `service` (业务基石)
- 标准的 MyBatis-Plus 操控代码。例如 `UserServiceImpl` 里蕴含了长命 `Token` 在 `StringRedisTemplate` 中的签发和销毁认证体系逻辑。

### 12. `utils` & `model` (数据运输与共享)
- `model`：充斥着 DTO, VO 和 Entity（遵循典型的 Alibaba Java 设计手册层级规范）。
- `UserHolder`：应用了 `ThreadLocal` 变量。当拦截器从 Redis 中验明金身后，在这里存放 `User` 上下文，供下游 `Controller` 或 `Service` 免参数随时拔取当前登录者资料，线程安全。

—— 完 ——
