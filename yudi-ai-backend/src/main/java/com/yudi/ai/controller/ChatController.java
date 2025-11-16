package com.yudi.ai.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yudi.ai.advisor.MyLoggerAdvisor;
import com.yudi.ai.agent.YdManus;
import com.yudi.ai.common.BaseResponse;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.exception.BusinessException;
import com.yudi.ai.exception.ThrowUtils;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.model.vo.ChatResponseVO;
import com.yudi.ai.rag.QueryRewriter;
import com.yudi.ai.service.ConversationMemoryService;
import com.yudi.ai.utils.SseEmitterUtil;
import com.yudi.ai.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 统一聊天接口
 * <p>
 * - 支持普通模式（RAG）和深度思考模式（Agent）
 * - 支持流式和非流式响应
 * - 统一管理会话
 */
@Slf4j
@RestController
@RequestMapping("/c")
public class ChatController {

    // 静态常量
    private static final String DEEP_THOUGHT_MODE = "deep_thought";
    private static final String DEFAULT_PROMPT = ResourceUtil.readUtf8Str("prompts/cook_app_system_prompt.md");

    // 注入服务
    @Resource
    private QueryRewriter queryRewriter;
    @Resource
    private ToolCallbackProvider toolCallbackProvider;
    @Resource
    private ConversationMemoryService conversationMemoryService;
    @Resource
    private YdManus ydManus;

    // AI模型及组件
    private ChatClient dashScopeChatClient;
    private final DocumentRetriever pgRetriever;

    // 用于延迟初始化的字段
    private final ChatClient.Builder chatClientBuilder;
    private final Object[] allToolInstances;

    /**
     * 请求体 DTO
     */
    @Data
    public static class ChatRequest {
        private String query;
        private String mode;
        private String conversationId;
    }

    public ChatController(ChatClient.Builder chatClientBuilder,
                          VectorStore pgVectorVectorStore,
                          Object[] allToolInstances) {
        this.pgRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(pgVectorVectorStore)
                .build();
        this.chatClientBuilder = chatClientBuilder;
        this.allToolInstances = allToolInstances;
    }

    @PostConstruct
    public void initializeChatClient() {
        ChatClient.Builder builder = chatClientBuilder
                .defaultSystem(DEFAULT_PROMPT)
                .defaultTools(allToolInstances)
                .defaultAdvisors(
                         new SimpleLoggerAdvisor()
                        ,new MyLoggerAdvisor()
                )
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.7)
                                .withTemperature(0.3)
                                .withMaxToken(2000)
                                .build()
                );

        if (toolCallbackProvider != null) {
            try {
                builder.defaultToolCallbacks(toolCallbackProvider.getToolCallbacks());
                log.info("成功添加MCP工具到ChatClient");
            } catch (Exception e) {
                log.warn("添加MCP工具失败: {}", e.getMessage());
            }
        } else {
            log.info("ToolCallbackProvider未配置，跳过MCP工具");
        }
        this.dashScopeChatClient = builder.build();
    }

    /**
     * 统一聊天接口（流式）
     */
    @PostMapping({"/chat/stream", "/chat/stream/{conversationId}"})
    public SseEmitter chatStream(@RequestBody ChatRequest chatRequest,
                                 @PathVariable(required = false) String conversationId) {
        String query = chatRequest.getQuery();
        String mode = chatRequest.getMode();
        log.info("统一聊天请求（流式）: {}, 模式: {}, 会话ID: {}", query, mode, conversationId);
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMETER_NULL, "查询内容不能为空");

        User user = UserHolder.getUser();
        if (user == null) {
            return SseEmitterUtil.error("用户未登录");
        }

        String finalConversationId = StrUtil.isNotBlank(conversationId) ? conversationId : conversationMemoryService.createConversation(user.getId());

        StrBuilder fullResponse = new StrBuilder();
        AtomicBoolean saved = new AtomicBoolean(false);
        Runnable saveTask = () -> {
            if (saved.compareAndSet(false, true) && StrUtil.isNotBlank(fullResponse.toString())) {
                saveConversationIfNeeded(finalConversationId, user.getId(), query, fullResponse.toString());
            }
        };

        try {
            Flux<String> dataFlux;
            if (StrUtil.equalsIgnoreCase(mode, DEEP_THOUGHT_MODE)) {
                // 深度思考模式
                dataFlux = Flux.defer(() -> {
                            ydManus.reset();
                            return ydManus.runStream(query);
                        })
                        .doOnError(error -> log.error("YdManus流式执行失败: {}", error.getMessage(), error))
                        .onErrorResume(error -> {
                            log.warn("YdManus流式执行失败，尝试非流式兜底: {}", error.getMessage());
                            String fallback = executeSyncDeepThought(query);
                            fullResponse.append(fallback);
                            saveTask.run();
                            return Flux.just(fallback);
                        })
                        .doFinally(signalType -> ydManus.reset());
            } else {
                // 普通RAG模式
                dataFlux = Flux.defer(() -> performStreamRagWithFallback(query, this.pgRetriever, "pgVector"))
                        .onErrorResume(error -> {
                            log.error("RAG聊天流式执行失败，尝试兜底: {}", error.getMessage(), error);
                            String fallback = performRagWithFallback(query, this.pgRetriever, "pgVector");
                            fullResponse.append(fallback);
                            saveTask.run();
                            return Flux.just(fallback);
                        });
            }

            Flux<SseEmitter.SseEventBuilder> eventFlux = Flux.concat(
                    Flux.just(SseEmitter.event().name("conversationId").data(finalConversationId)),
                    dataFlux.doOnNext(fullResponse::append).doOnComplete(saveTask).map(chunk -> SseEmitter.event().name("message").data(chunk))
            );

            return SseEmitterUtil.fromEventFlux(eventFlux);

        } catch (Exception e) {
            log.error("聊天初始化失败: {}", e.getMessage(), e);
            return SseEmitterUtil.error("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 统一聊天接口（非流式）
     */
    @PostMapping({"/chat", "/chat/{conversationId}"})
    public BaseResponse<ChatResponseVO> chat(@RequestBody ChatRequest chatRequest,
                                             @PathVariable(required = false) String conversationId) {
        String query = chatRequest.getQuery();
        String mode = chatRequest.getMode();
        log.info("统一聊天请求（非流式）: {}, 模式: {}, 会话ID: {}", query, mode, conversationId);
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMETER_NULL, "查询内容不能为空");

        User user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }

        String finalConversationId = StrUtil.isNotBlank(conversationId) ? conversationId : conversationMemoryService.createConversation(user.getId());

        try {
            String answer;
            if (StrUtil.equalsIgnoreCase(mode, DEEP_THOUGHT_MODE)) {
                // 深度思考模式
                answer = executeSyncDeepThought(query);
            } else {
                // 普通RAG模式
                answer = performRagWithFallback(query, this.pgRetriever, "pgVector");
            }

            saveConversationIfNeeded(finalConversationId, user.getId(), query, answer);

            ChatResponseVO response = new ChatResponseVO();
            response.setAnswer(answer);
            response.setConversationId(finalConversationId);
            return BaseResponse.success(response);

        } catch (Exception e) {
            log.error("聊天执行失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "聊天执行失败: " + e.getMessage());
        }
    }

    // --- 私有辅助方法 ---

    private String executeSyncDeepThought(String query) {
        try {
            ydManus.reset();
            return ydManus.run(query);
        } catch (Exception e) {
            log.error("YdManus同步执行失败: {}", e.getMessage(), e);
            return "深度思考时出现错误，请稍后再试。";
        } finally {
            ydManus.reset();
        }
    }

    private String performRagWithFallback(String query, DocumentRetriever retriever, String retrieverName) {
        return performRag(query, retriever, retrieverName, prompt -> prompt.call().content());
    }

    private Flux<String> performStreamRagWithFallback(String query, DocumentRetriever retriever, String retrieverName) {
        return performRag(query, retriever, retrieverName, prompt -> prompt.stream().content());
    }

    private <T> T performRag(String query, DocumentRetriever retriever, String retrieverName,
                             Function<ChatClient.ChatClientRequestSpec, T> responseFunc) {
        String rewrittenQuery = StrUtil.length(query) > 20 ? queryRewriter.doQueryRewriter(query) : query;
        boolean wasRewritten = !StrUtil.equals(query, rewrittenQuery);
        if (wasRewritten) {
            log.info("查询重写:{} ->  {}", query, rewrittenQuery);
        }

        List<Document> documents = retriever.retrieve(new Query(rewrittenQuery));

        if (CollUtil.isNotEmpty(documents)) {
            log.info("从 {} 检索到 {} 个相关文档。", retrieverName, documents.size());
            String context = CollUtil.join(CollUtil.map(documents, Document::getText, true), "\n---\n");
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
            return responseFunc.apply(dashScopeChatClient.prompt().user(userMessage));
        } else {
            log.info("未从 {} 检索到相关文档，使用通用知识流程回答。", retrieverName);
            return responseFunc.apply(dashScopeChatClient.prompt().user(query));
        }
    }

    private void saveConversationIfNeeded(String conversationId, Long userId, String userInput, String aiResponse) {
        if (StrUtil.isAllBlank(conversationId, userInput, aiResponse) || userId == null) {
            return;
        }
        try {
            conversationMemoryService.saveConversationRound(conversationId, userId, userInput, aiResponse);
        } catch (Exception e) {
            log.error("保存对话记录失败: {}", e.getMessage(), e);
        }
    }
}

