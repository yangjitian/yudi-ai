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
import com.yudi.ai.model.vo.ConversationMemoryVO;
import com.yudi.ai.rag.QueryRewriter;
import com.yudi.ai.service.ConversationMemoryService;
import com.yudi.ai.utils.SseEmitterUtil;
import com.yudi.ai.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 最终完美版 ChatController（已彻底解决 RAG 污染问题）
 * 核心思想：RAG 检索内容只以 SystemMessage 形式出现，永不进入历史！
 */
@Slf4j
@RestController
@RequestMapping("/c")
public class ChatController {

    private static final String DEEP_THOUGHT_MODE = "deep_thought";
    private static final String DEFAULT_PROMPT = ResourceUtil.readUtf8Str("prompts/cook_app_system_prompt.md");
    private static final int MAX_HISTORY_ROUNDS = 6; // 最近 6 轮（user + assistant）

    @Resource private QueryRewriter queryRewriter;
    @Resource private ToolCallbackProvider toolCallbackProvider;
    @Resource private ConversationMemoryService conversationMemoryService;
    @Resource private YdManus ydManus;

    private ChatClient dashScopeChatClient;
    private final DocumentRetriever pgRetriever;
    private final ChatClient.Builder chatClientBuilder;
    private final Object[] allToolInstances;

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
                .defaultAdvisors(new SimpleLoggerAdvisor(), new MyLoggerAdvisor())
                .defaultOptions(DashScopeChatOptions.builder()
                        .withTopP(0.7)
                        .withTemperature(0.3)
                        .withMaxToken(2000)
                        .build());

        if (toolCallbackProvider != null) {
            try {
                builder.defaultToolCallbacks(toolCallbackProvider.getToolCallbacks());
                log.info("成功添加MCP工具到ChatClient");
            } catch (Exception e) {
                log.warn("添加MCP工具失败: {}", e.getMessage());
            }
        }
        this.dashScopeChatClient = builder.build();
    }

    // ================================ 流式聊天 ================================
    @PostMapping({"/chat/stream", "/chat/stream/{conversationId}"})
    public SseEmitter chatStream(@RequestBody ChatRequest chatRequest,
                                 @PathVariable(required = false) String conversationId) {
        String query = chatRequest.getQuery();
        String mode = chatRequest.getMode();
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMETER_NULL, "查询内容不能为空");

        User user = UserHolder.getUser();
        if (user == null) return SseEmitterUtil.error("用户未登录");

        String finalConversationId = StrUtil.isNotBlank(conversationId)
                ? conversationId : conversationMemoryService.createConversation(user.getId());

        StrBuilder fullResponse = new StrBuilder();
        AtomicBoolean saved = new AtomicBoolean(false);
        Runnable saveTask = () -> {
            if (saved.compareAndSet(false, true) && StrUtil.isNotBlank(fullResponse.toString())) {
                saveConversationIfNeeded(finalConversationId, user.getId(), query, fullResponse.toString());
            }
        };

        Flux<String> dataFlux;
        if (StrUtil.equalsIgnoreCase(mode, DEEP_THOUGHT_MODE)) {
            dataFlux = Flux.defer(() -> {
                        ydManus.reset();
                        return ydManus.runStream(query);
                    })
                    .doOnError(e -> log.error("YdManus流式失败: {}", e.getMessage(), e))
                    .onErrorResume(e -> {
                        String fallback = executeSyncDeepThought(query);
                        fullResponse.append(fallback);
                        saveTask.run();
                        return Flux.just(fallback);
                    })
                    .doFinally(s -> ydManus.reset());
        } else {
            dataFlux = performStreamRagWithHistory(query, finalConversationId)
                    .onErrorResume(e -> {
                        log.error("RAG流式失败，兜底非流式: {}", e.getMessage());
                        String fallback = performRagWithHistory(query, finalConversationId);
                        fullResponse.append(fallback);
                        saveTask.run();
                        return Flux.just(fallback);
                    });
        }

        Flux<SseEmitter.SseEventBuilder> eventFlux = Flux.concat(
                Flux.just(SseEmitter.event().name("conversationId").data(finalConversationId)),
                dataFlux.doOnNext(fullResponse::append)
                        .doOnComplete(saveTask)
                        .filter(Objects::nonNull)
                        .map(chunk -> SseEmitter.event().name("message").data(chunk))
        );

        return SseEmitterUtil.fromEventFlux(eventFlux);
    }

    // ================================ 非流式聊天 ================================
    @PostMapping({"/chat", "/chat/{conversationId}"})
    public BaseResponse<ChatResponseVO> chat(@RequestBody ChatRequest chatRequest,
                                             @PathVariable(required = false) String conversationId) {
        String query = chatRequest.getQuery();
        String mode = chatRequest.getMode();
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMETER_NULL, "查询内容不能为空");

        User user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");

        String finalConversationId = StrUtil.isNotBlank(conversationId)
                ? conversationId : conversationMemoryService.createConversation(user.getId());

        String answer;
        if (StrUtil.equalsIgnoreCase(mode, DEEP_THOUGHT_MODE)) {
            answer = executeSyncDeepThought(query);
        } else {
            answer = performRagWithHistory(query, finalConversationId);
        }

        saveConversationIfNeeded(finalConversationId, user.getId(), query, answer);

        ChatResponseVO response = new ChatResponseVO();
        response.setAnswer(answer);
        response.setConversationId(finalConversationId);
        return BaseResponse.success(response);
    }

    // ================================ 核心 RAG 方法（彻底根治版） ================================

    private String performRagWithHistory(String query, String conversationId) {
        return performRagInternal(query, conversationId, spec -> spec.call().content());
    }

    private Flux<String> performStreamRagWithHistory(String query, String conversationId) {
        return performRagInternal(query, conversationId, spec -> spec.stream().content());
    }

    /** 核心：RAG 内容只以 SystemMessage 出现，永不污染历史 */
    private <T> T performRagInternal(String query,
                                     String conversationId,
                                     Function<ChatClient.ChatClientRequestSpec, T> responseFunc) {

        // 1. 查询重写
        String rewrittenQuery = StrUtil.length(query) > 20 ? queryRewriter.doQueryRewriter(query) : query;

        // 2. 向量检索
        List<Document> documents = pgRetriever.retrieve(new Query(rewrittenQuery));

        // 3. 构建绝对干净的历史对话（只包含真实用户输入和AI回复）
        List<Message> historyMessages = buildCleanHistory(conversationId);

        // 4. 构造本次请求的完整消息列表
        List<Message> promptMessages = new ArrayList<>(historyMessages);

        // 如果有检索到内容 → 作为 SystemMessage 加入，且只活在这一轮！
        if (CollUtil.isNotEmpty(documents)) {
            String context = CollUtil.join(CollUtil.map(documents, Document::getText, true), "\n\n");

            String ragSystemPrompt = StrUtil.format(
                    """
                    【重要指令】
                    你必须优先使用以下检索到的真实知识来回答用户问题。
                    如果检索内容不足或不相关，再使用你的通用知识补充。
                    绝不要编造不存在的事实。

                    【检索到的真实知识】
                    {}
                    """, context);

            promptMessages.add(0, new SystemMessage(ragSystemPrompt)); // 放在最前面效果最佳
        }

        // 最后加入用户当前真实说的这句话（原始 query）
        promptMessages.add(new UserMessage(query));

        // 5. 创建 Prompt 并调用模型
        Prompt prompt = new Prompt(promptMessages);
        return responseFunc.apply(dashScopeChatClient.prompt(prompt));
    }

    /** 构建绝对干净的历史（永不被 RAG 污染） */
    private List<Message> buildCleanHistory(String conversationId) {
        List<ConversationMemoryVO> rawHistory = conversationMemoryService.getConversationHistory(
                conversationId, UserHolder.getUser().getId());

        if (CollUtil.isEmpty(rawHistory)) {
            return new ArrayList<>();
        }

        // 限制最近 6 轮
        if (rawHistory.size() > MAX_HISTORY_ROUNDS) {
            rawHistory = rawHistory.subList(rawHistory.size() - MAX_HISTORY_ROUNDS, rawHistory.size());
        }

        List<Message> messages = new ArrayList<>();
        for (ConversationMemoryVO vo : rawHistory) {
            String userInput = vo.getUserInput();
            String aiResponse = vo.getAiResponse();

            // 极端严格过滤，任何有 RAG 痕迹的直接丢弃
            if (StrUtil.isNotBlank(userInput)
                    && userInput.length() < 1000
                    && !userInput.contains("【背景知识】")
                    && !userInput.contains("【检索")
                    && !userInput.contains("参考信息")
                    && !userInput.contains("---")
                    && !userInput.contains("重要指令")) {

                messages.add(new UserMessage(userInput));
            }

            if (StrUtil.isNotBlank(aiResponse)) {
                messages.add(new AssistantMessage(aiResponse));
            }
        }
        return messages;
    }

    // ================================ 其他方法 ================================

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

    private void saveConversationIfNeeded(String conversationId, Long userId, String rawUserInput, String aiResponse) {
        if (StrUtil.isAllBlank(conversationId, rawUserInput, aiResponse) || userId == null) return;

        // 再次保险：防止任何 RAG 包装消息被存进去
        if (rawUserInput.contains("【重要指令】") || rawUserInput.contains("【检索到的真实知识】") || rawUserInput.length() > 800) {
            log.warn("检测到RAG系统消息试图被存为用户输入，已阻止！conversationId: {}", conversationId);
            return;
        }

        try {
            conversationMemoryService.saveConversationRound(conversationId, userId, rawUserInput, aiResponse);
        } catch (Exception e) {
            log.error("保存对话记录失败: {}", e.getMessage(), e);
        }
    }
}