package com.yudi.ai.controller;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import com.yudi.ai.common.BaseResponse;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.agent.YdManus;
import com.yudi.ai.exception.BusinessException;
import com.yudi.ai.exception.ThrowUtils;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.model.vo.ChatResponseVO;
import com.yudi.ai.service.ConversationMemoryService;
import com.yudi.ai.utils.SseEmitterUtil;
import com.yudi.ai.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * YdManus智能体接口控制器
 * <p>
 * 提供基于YdManus智能体的对话接口，支持非流式和流式两种响应方式
 */
@Slf4j
@RestController
@RequestMapping("/yd_manus")
public class YdManusController {

    @Resource
    private YdManus ydManus;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    /**
     * YdManus智能体对话接口（非流式）
     *
     * @param query 用户查询
     * @param conversationId 会话ID（可选，路径参数）
     * @return AI生成的回答
     */
    @GetMapping({"/chat", "/chat/{conversationId}"})
    public BaseResponse<ChatResponseVO> chat(@RequestParam(value = "query") String query,
                                             @PathVariable(required = false) String conversationId) {
        log.info("YdManus聊天请求: {}, 会话ID: {}", query, conversationId);
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMETER_NULL, "查询内容不能为空");

        User user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }

        if (StrUtil.isBlank(conversationId)) {
            conversationId = conversationMemoryService.createConversation(user.getId());
        }

        try {
            String answer = executeSyncChat(query);
            saveConversationIfNeeded(conversationId, user.getId(), query, answer);

            ChatResponseVO response = new ChatResponseVO();
            response.setAnswer(answer);
            response.setConversationId(conversationId);
            return BaseResponse.success(response);
        } catch (Exception e) {
            log.error("YdManus执行失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "深度思考执行失败: " + e.getMessage());
        }
    }

    /**
     * YdManus智能体对话接口（流式，使用SseEmitter）
     *
     * @param query 用户查询
     * @param conversationId 会话ID（可选，路径参数）
     * @return SSE流式响应
     */
    @GetMapping({"/chat/stream", "/chat/stream/{conversationId}"})
    public SseEmitter chatStream(@RequestParam(value = "query") String query,
                                 @PathVariable(required = false) String conversationId) {
        log.info("YdManus聊天请求（流式）: {}, 会话ID: {}", query, conversationId);
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
            Flux<String> dataFlux = Flux.defer(() -> {
                        ydManus.reset();
                        return ydManus.runStream(query);
                    })
                    .doOnNext(fullResponse::append)
                    .doOnError(error -> log.error("YdManus流式执行失败: {}", error.getMessage(), error))
                    .onErrorResume(error -> {
                        log.warn("YdManus流式执行失败，尝试非流式兜底: {}", error.getMessage());
                        String fallback = executeSyncChat(query);
                        fullResponse.append(fallback);
                        saveTask.run();
                        return Flux.just(fallback);
                    })
                    .doOnComplete(saveTask::run)
                    .doFinally(signalType -> {
                        ydManus.reset();
                        log.info("YdManus流式执行完成，信号类型: {}", signalType);
                    });

            Flux<SseEmitter.SseEventBuilder> eventFlux = Flux.concat(
                    Flux.just(SseEmitter.event().name("conversationId").data(finalConversationId)),
                    dataFlux.map(chunk -> SseEmitter.event().name("message").data(chunk))
            );

            return SseEmitterUtil.fromEventFlux(eventFlux);

        } catch (Exception e) {
            log.error("YdManus初始化失败: {}", e.getMessage(), e);
            ydManus.reset();
            return SseEmitterUtil.error("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 执行一次非流式对话（提供给外部和兜底调用）
     */
    private String executeSyncChat(String query) {
        try {
            ydManus.reset();
            return ydManus.run(query);
        } catch (Exception e) {
            log.error("YdManus执行失败: {}", e.getMessage(), e);
            return "执行失败: " + e.getMessage();
        } finally {
            ydManus.reset();
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
        }
    }
}
