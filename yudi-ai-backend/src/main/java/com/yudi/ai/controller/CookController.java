package com.yudi.ai.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yudi.ai.advisor.MyLoggerAdvisor;
import com.yudi.ai.common.BaseResponse;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.exception.ThrowUtils;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.model.vo.ChatResponseVO;
import com.yudi.ai.rag.QueryRewriter;
import com.yudi.ai.service.ConversationMemoryService;
import com.yudi.ai.utils.SseEmitterUtil;
import com.yudi.ai.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 交流接口：
 *
 * - 基础的聊天功能（简单调用和流式调用）
 * - 基于不同知识库（云知识库、本地PostgreSQL向量数据库）的检索增强生成（RAG）功能
 * - 结构化的菜谱报告生成功能
 */
@Slf4j
@RestController
@RequestMapping("/cook")
public class CookController {

    // 使用静态初始化块，避免每次类加载时重复读取
    private static final String DEFAULT_PROMPT;
    static {
        DEFAULT_PROMPT = ResourceUtil.readUtf8Str("prompts/cook_app_system_prompt.md");
    }

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    // 通用的、无RAG增强的ChatClient。
    private ChatClient dashScopeChatClient;
    // 阿里云知识库的文档检索器。
    private final DocumentRetriever cloudRetriever;
    // 本地PostgreSQL向量数据库的文档检索器。
    private final VectorStoreDocumentRetriever pgRetriever;

    /**
     * 初始化所有必要的组件。
     *
     * @param chatClientBuilder Spring AI提供的一个ChatClient构建器，用于创建ChatClient实例。
     * @param documentRetriever 注入的阿里云知识库文档检索器Bean。
     * @param pgVectorVectorStore 注入的本地PostgreSQL向量数据库实例。
     * @param allToolInstances 注入的所有工具实例数组。
     */
    public CookController(ChatClient.Builder chatClientBuilder
            ,DocumentRetriever documentRetriever
            ,VectorStore pgVectorVectorStore
            ,Object[] allToolInstances) {

        // 初始化云知识库检索器
        this.cloudRetriever = documentRetriever;

        // 初始化本地pgVector数据库检索器
        this.pgRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(pgVectorVectorStore)
                .build();

        // 保存构建器和工具实例，在@PostConstruct中初始化ChatClient
        this.chatClientBuilder = chatClientBuilder;
        this.allToolInstances = allToolInstances;
    }

    // 用于延迟初始化的字段
    private final ChatClient.Builder chatClientBuilder;
    private final Object[] allToolInstances;

    /**
     * 在所有依赖注入完成后初始化ChatClient
     */
    @PostConstruct
    public void initializeChatClient() {
        // 初始化 ChatClient，默认的系统提示，无任何强制性的RAG顾问。负责最终的思考和回答。
        ChatClient.Builder builder = chatClientBuilder
                .defaultSystem(DEFAULT_PROMPT)
                .defaultTools(allToolInstances)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MyLoggerAdvisor()
                ).defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.7)
                                .withTemperature(0.3)  // 降低temperature提高响应速度和确定性
                                .withMaxToken(2000)     // 限制最大token数，加快响应
                                .build()
                );

        // 如果MCP工具可用，则添加MCP工具
        if (toolCallbackProvider != null) {
            try {
                builder.defaultToolCallbacks(toolCallbackProvider.getToolCallbacks());
                log.info("成功添加MCP工具到ChatClient");
            } catch (Exception e) {
                log.warn("添加MCP工具失败，继续使用其他工具: {}", e.getMessage());
            }
        } else {
            log.info("ToolCallbackProvider未配置，跳过MCP工具");
        }

        this.dashScopeChatClient = builder.build();
    }


    // RAG 增强（非流式）
    private String performRagWithFallback(String query, DocumentRetriever retriever, String retrieverName) {
        return performRag(query, retriever, retrieverName, prompt -> prompt.call().content());
    }


    // RAG 增强（流式）
    private Flux<String> performStreamRagWithFallback(String query, DocumentRetriever retriever, String retrieverName) {
        return performRag(query, retriever, retrieverName, prompt -> prompt.stream().content());
    }

    /**
     * RAG核心处理逻辑（统一方法）
     *
     * @param query         用户的原始查询
     * @param retriever     文档检索器
     * @param retrieverName 检索器名称
     * @param responseFunc  响应处理函数（call或stream）
     * @param <T>           返回类型（String或Flux<String>）
     * @return AI生成的回答
     */
    private <T> T performRag(String query, DocumentRetriever retriever, String retrieverName,
                             Function<ChatClient.ChatClientRequestSpec, T> responseFunc) {
        // 1. 查询重写，优化问题（对于简单查询，可以跳过重写以提高速度）
        String rewrittenQuery = StrUtil.length(query) > 20 ? queryRewriter.doQueryRewriter(query) : query;
        boolean wasRewritten = !StrUtil.equals(query, rewrittenQuery);
        if (wasRewritten) {
            log.info("查询重写:{} ->  {}", query, rewrittenQuery);
        }

        // 2. 使用指定的检索器和重写后的查询，从知识库中检索相关文档
        List<Document> documents = retriever.retrieve(new Query(rewrittenQuery));

        // 3. 判断是否找到了相关文档
        if (CollUtil.isNotEmpty(documents)) {
            log.info("从 {} 检索到 {} 个相关文档，构建RAG上下文。", retrieverName, documents.size());

            // 将所有检索到的文档内容合并成一个大的字符串，作为背景知识
            String context = CollUtil.join(
                    CollUtil.map(documents, Document::getText, true),
                    "\n---\n"
            );

            // 构建用户提示消息
            String userMessage = StrUtil.format(
                    """
                            请基于以下参考信息回答用户的问题。

                            【背景知识】
                            {}

                             - 优先使用背景知识回答
                             - 背景知识不足时可以使用你自己的通用知识补充
                             - 必须针对用户原始问题回答

                            【问题信息】
                             - 用户原始问题: {}{}""",
                    context, query,
                    wasRewritten ? StrUtil.format("\n检索优化版本: {}(仅供参考)", rewrittenQuery) : ""
            );

            // 调用通用的dashScopeChatClient来生成回答
            return responseFunc.apply(dashScopeChatClient.prompt().user(userMessage));
        } else {
            // 如果没有从知识库中找到任何文档，则执行兜底方案
            log.info("未从 {} 检索到相关文档，使用原始查询通过通用知识流程回答。", retrieverName);
            return responseFunc.apply(dashScopeChatClient.prompt(query));
        }
    }

    /**
     * 普通聊天接口（流式）
     * @param query 用户查询
     * @param conversationId 会话ID（可选，路径参数）
     * @return SSE流式响应
     */
    @GetMapping({"/pg/chat/stream", "/pg/chat/stream/{conversationId}"})
    public SseEmitter pgChatStream(@RequestParam("query") String query,
                                   @PathVariable(required = false) String conversationId) {
        log.info("普通聊天请求（pgVector流式）: {}, 会话ID: {}", query, conversationId);
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMETER_NULL, "查询内容不能为空");

        User user = UserHolder.getUser();
        if (user == null) {
            return SseEmitterUtil.error("用户未登录");
        }

        if (StrUtil.isBlank(conversationId)) {
            conversationId = conversationMemoryService.createConversation(user.getId());
        }

        final String finalConversationId = conversationId;
        StrBuilder fullResponse = StrBuilder.create();
        AtomicBoolean saved = new AtomicBoolean(false);
        Runnable saveTask = () -> {
            if (saved.compareAndSet(false, true) && StrUtil.isNotBlank(fullResponse.toString())) {
                saveConversationIfNeeded(finalConversationId, user.getId(), query, fullResponse.toString());
            }
        };

        try {
            Flux<String> dataFlux = Flux.defer(() -> performStreamRagWithFallback(query, this.pgRetriever, "pgVector"))
                    .doOnNext(fullResponse::append)
                    .onErrorResume(error -> {
                        log.error("聊天流式执行失败，尝试兜底: {}", error.getMessage(), error);
                        String fallback = performRagWithFallback(query, this.pgRetriever, "pgVector");
                        fullResponse.append(fallback);
                        saveTask.run();
                        return Flux.just(fallback);
                    })
                    .doOnComplete(saveTask);

            Flux<SseEmitter.SseEventBuilder> eventFlux = Flux.concat(
                    Flux.just(SseEmitter.event().name("conversationId").data(finalConversationId)),
                    dataFlux.map(chunk -> SseEmitter.event().name("message").data(chunk))
            );

            return SseEmitterUtil.fromEventFlux(eventFlux);

        } catch (Exception e) {
            log.error("聊天初始化失败: {}", e.getMessage(), e);
            return SseEmitterUtil.error("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 普通聊天接口（非流式）
     * @param query 用户查询
     * @param conversationId 会话ID（可选，路径参数）
     * @return AI回答
     */
    @GetMapping({"/pg/chat", "/pg/chat/{conversationId}"})
    public BaseResponse<ChatResponseVO> pgChat(@RequestParam("query") String query,
                                               @PathVariable(required = false) String conversationId) {
        log.info("普通聊天请求（pgVector）: {}, 会话ID: {}", query, conversationId);
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMETER_NULL, "查询内容不能为空");

        User user = UserHolder.getUser();
        if (user == null) {
            throw new com.yudi.ai.exception.BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }

        // 如果没有会话ID，创建新会话
        if (StrUtil.isBlank(conversationId)) {
            conversationId = conversationMemoryService.createConversation(user.getId());
        }

        try {
            String answer = performRagWithFallback(query, this.pgRetriever, "pgVector");
            
            // 保存对话
            saveConversationIfNeeded(conversationId, user.getId(), query, answer);

            ChatResponseVO response = new ChatResponseVO();
            response.setAnswer(answer);
            response.setConversationId(conversationId);
            return BaseResponse.success(response);

        } catch (Exception e) {
            log.error("聊天执行失败: {}", e.getMessage(), e);
            throw new com.yudi.ai.exception.BusinessException(ErrorCode.SYSTEM_ERROR, "聊天执行失败: " + e.getMessage());
        }
    }

    /**
     * 保存对话记录（如果需要）
     */
    private void saveConversationIfNeeded(String conversationId, Long userId, String userInput, String aiResponse) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return;
        }
        if (StrUtil.isBlank(userInput) || StrUtil.isBlank(aiResponse)) {
            return;
        }

        try {
            conversationMemoryService.saveConversationRound(conversationId, userId, userInput, aiResponse);
        } catch (Exception e) {
            log.error("保存对话记录失败: {}", e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }
//
//    /**
//     * RAG增强聊天端点 - 基于阿里云知识库。
//     * @param query 用户查询。
//     * @return AI结合云知识库生成的回答。
//     */
//    @GetMapping("/cloud/chat")
//    public String ragChat(@RequestParam(value = "query", required = true) String query) {
//        log.info("云知识库 RAG聊天请求: {}", query);
//        return performRagWithFallback(query, this.cloudRetriever, "云知识库");
//    }
//
//    /**
//     * RAG增强聊天端点 - 基于阿里云知识库（流式，使用SseEmitter）。
//     * 优先使用云知识库检索，检索不到时使用ChatClient作为兜底。
//     *
//     * @param query 用户查询。
//     * @return SSE流式响应。
//     */
//    @GetMapping("/cloud/chat/stream")
//    public SseEmitter chatStream(@RequestParam(value = "query") String query) {
//        log.info("云知识库 RAG聊天请求（流式）: {}", query);
//        Flux<String> flux = performStreamRagWithFallback(query, this.cloudRetriever, "云知识库");
//        return SseEmitterUtil.fromFlux(flux);
//    }
}
