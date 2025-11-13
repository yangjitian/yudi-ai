package com.yudi.ai.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yudi.ai.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具（ToolCallback类型，通常是MCP工具）
    private final ToolCallback[] availableTools;

    // 本地工具实例（带@Tool注解的工具对象）
    private Object[] localToolInstances;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    // LLM 大模型客户端
    private ChatClient chatClient;

    // 系统提示词
    private String systemPrompt;

    // 下一步提示词（每步注入）
    private String nextStepPrompt;

    /**
     * 构造函数
     *
     * @param availableTools 可用的工具列表
     */
    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.localToolInstances = new Object[0];
        // ToolCallingManager会从ChatResponse中获取工具调用信息并执行
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .withTemperature(0.3)  // 降低temperature提高响应速度和确定性
                .withMaxToken(2000)    // 限制最大token数，加快响应
                .withTopP(0.8)         // 稍微提高TopP以保持一定创造性
                .build();
    }

    /**
     * 验证消息序列是否符合规范
     */
    private void validateMessageSequence() {
        if (!log.isDebugEnabled()) {
            return;
        }

        log.debug("当前消息序列（共{}条）：", getMemory().size());
        for (int i = 0; i < getMemory().size(); i++) {
            Message msg = getMemory().get(i);
            String msgInfo = String.format("  [%d] role=%s, type=%s",
                    i, msg.getMessageType(), msg.getClass().getSimpleName());

            // 如果是AssistantMessage，检查是否有工具调用
            if (msg instanceof AssistantMessage) {
                AssistantMessage aMsg = (AssistantMessage) msg;
                if (CollUtil.isNotEmpty(aMsg.getToolCalls())) {
                    msgInfo += String.format(", toolCalls=%d", aMsg.getToolCalls().size());
                }
            }
            // 如果是ToolResponseMessage，显示工具响应数量
            else if (msg instanceof ToolResponseMessage) {
                ToolResponseMessage tMsg = (ToolResponseMessage) msg;
                if (CollUtil.isNotEmpty(tMsg.getResponses())) {
                    msgInfo += String.format(", responses=%d", tMsg.getResponses().size());
                }
            }

            log.debug(msgInfo);
        }
    }

    /**
     * 处理当前状态并决定下一步行动
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    @Override
    public boolean think() {
        // 验证消息序列
        validateMessageSequence();

        // 1、校验提示词，拼接用户提示词
        String effectiveSystemPrompt = getSystemPrompt();
        if (StrUtil.isNotBlank(getNextStepPrompt()) && getCurrentStep() > 1) {
            effectiveSystemPrompt = StrUtil.format("{}\n\n{}", getSystemPrompt(), getNextStepPrompt());
        }

        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMemory();

        try {
            // 添加调试日志，检查工具注册情况
            log.debug("可用工具数量 - ToolCallback: {}, 本地工具: {}",
                    CollUtil.size(availableTools),
                    CollUtil.size(localToolInstances));
            log.debug("当前消息数量: {}", messageList.size());

            // 限制memory长度，避免上下文过长
            trimMemoryIfNeeded();

            // 创建Prompt对象
            Prompt prompt = new Prompt(messageList, this.chatOptions);

            // 记录LLM调用开始时间
            long thinkStartTime = System.currentTimeMillis();

            // 调用LLM获取响应
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(effectiveSystemPrompt)
                    .call()
                    .chatResponse();

            // 记录LLM调用耗时
            long thinkDuration = System.currentTimeMillis() - thinkStartTime;
            log.info("{} LLM思考耗时: {}ms", getName(), thinkDuration);

            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;

            // 3、解析工具调用结果
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 输出思考结果
            String result = assistantMessage.getText();
            log.info("{}的思考：\n {}", getName(), result);
            log.info("{}选择了 {} 个工具来使用", getName(), CollUtil.size(toolCallList));

            if (CollUtil.isNotEmpty(toolCallList)) {
                StringBuilder toolCallInfo = new StringBuilder("工具调用列表：\n");
                for (int i = 0; i < toolCallList.size(); i++) {
                    AssistantMessage.ToolCall toolCall = toolCallList.get(i);
                    toolCallInfo.append(String.format("  [%d] %s - 参数: %s\n",
                            i + 1, toolCall.name(), toolCall.arguments()));
                }
                log.info(toolCallInfo.toString().trim());
            }

            // 检查文本中是否包含terminate调用（作为后备方案）
            boolean hasTerminateInText = false;
            if (StrUtil.isNotBlank(result) && CollUtil.isEmpty(toolCallList)) {
                String lowerResult = result.toLowerCase();
                hasTerminateInText = (StrUtil.contains(lowerResult, "\"name\":\"terminate\"") ||
                        StrUtil.contains(lowerResult, "\"name\": \"terminate\"") ||
                        (StrUtil.contains(lowerResult, "function_call") &&
                                StrUtil.contains(lowerResult, "terminate"))) &&
                        StrUtil.contains(lowerResult, "name");
            }

            // 如果不需要调用工具，返回 false
            if (CollUtil.isEmpty(toolCallList)) {
                // 检查文本中是否明确表示要终止
                if (hasTerminateInText) {
                    log.warn("检测到文本中包含terminate调用，但工具调用解析失败。直接终止任务。");
                    setState(AgentState.FINISHED);
                    getMemory().add(assistantMessage);
                    return false;
                }

                // 判断是否应该结束任务
                boolean shouldFinish = false;

                // 策略1: 如果是第一步且没有工具调用，说明已经给出了完整回答
                if (getCurrentStep() == 1) {
                    shouldFinish = true;
                    log.info("第一步完成，LLM已给出完整回答且无需工具调用，任务完成");
                }
                // 策略2: 如果回答看起来完整
                else if (StrUtil.isNotBlank(result) && result.length() > 50) {
                    String lowerResult = result.toLowerCase();
                    boolean hasSuggestiveQuestion = StrUtil.containsAny(lowerResult,
                            "要不要", "是否", "要不要我", "需要我", "只需说");

                    if (hasSuggestiveQuestion) {
                        shouldFinish = true;
                        log.info("检测到LLM给出了建议性询问，这是最终回答，任务完成");
                    }
                }

                if (shouldFinish) {
                    setState(AgentState.FINISHED);
                }

                // 只有不调用工具时，才需要手动记录助手消息
                getMemory().add(assistantMessage);
                return false;
            } else {
                // 记录助手消息，确保工具调用上下文完整
                getMemory().add(assistantMessage);
                return true;
            }
        } catch (Exception e) {
            log.error("{}的思考过程遇到了问题：{}", getName(), e.getMessage(), e);
            getMemory().add(new AssistantMessage(
                    StrUtil.format("处理时遇到了错误：{}", e.getMessage())));
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }

        // 记录工具调用开始时间
        long actStartTime = System.currentTimeMillis();
        AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        int toolCallCount = CollUtil.size(toolCalls);
        log.info("{} 开始执行 {} 个工具调用", getName(), toolCallCount);

        // 执行工具调用
        // 重要：创建一个新的Prompt，只包含当前的AssistantMessage
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(assistantMessage);
        Prompt prompt = new Prompt(promptMessages, this.chatOptions);

        // 执行工具
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(
                prompt, toolCallChatResponse);

        // 记录工具调用耗时
        long actDuration = System.currentTimeMillis() - actStartTime;
        log.info("{} 工具执行完成，耗时: {}ms (平均每个工具: {}ms)",
                getName(), actDuration, toolCallCount > 0 ? actDuration / toolCallCount : 0);

        // 从执行结果中获取工具响应消息
        List<Message> resultMessages = toolExecutionResult.conversationHistory();

        // 查找并添加ToolResponseMessage到memory
        ToolResponseMessage toolResponseMessage = null;
        for (Message msg : resultMessages) {
            if (msg instanceof ToolResponseMessage) {
                toolResponseMessage = (ToolResponseMessage) msg;
                // 确保添加工具响应消息到memory
                getMemory().add(toolResponseMessage);
                log.debug("已添加工具响应消息，responses数量: {}",
                        CollUtil.size(toolResponseMessage.getResponses()));
                break;
            }
        }

        // 如果没有找到工具响应消息，尝试从最后一条消息获取
        if (toolResponseMessage == null) {
            Message lastMsg = CollUtil.getLast(resultMessages);
            if (lastMsg instanceof ToolResponseMessage) {
                toolResponseMessage = (ToolResponseMessage) lastMsg;
                getMemory().add(toolResponseMessage);
            } else {
                log.error("未能从工具执行结果中获取ToolResponseMessage");
                // 创建一个默认的工具响应消息
                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : toolCalls) {
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            toolCall.id(),
                            toolCall.name(),
                            "工具执行完成但未返回结果"
                    ));
                }
                toolResponseMessage = new ToolResponseMessage(toolResponses);
                getMemory().add(toolResponseMessage);
            }
        }

        // 限制memory长度，避免上下文过长
        trimMemoryIfNeeded();

        // 验证消息序列
        validateMessageSequence();

        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> {
                    String toolName = response.name();
                    return StrUtil.equalsAnyIgnoreCase(toolName,
                            "doTerminate", "terminate") ||
                            StrUtil.containsIgnoreCase(toolName, "terminate");
                });

        if (terminateToolCalled) {
            log.info("检测到终止工具调用，任务结束");
            setState(AgentState.FINISHED);
        }

        // 构建结果字符串
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> StrUtil.format("工具 {} 返回的结果：{}",
                        response.name(), response.responseData()))
                .collect(Collectors.joining("\n"));
        log.info(results);

        return results;
    }

    /**
     * 流式执行Agent（返回Flux<String>）
     *
     * @param userInput 用户输入
     * @return 流式响应
     */
    public Flux<String> runStream(String userInput) {
        // 基础校验
        if (this.getState() != AgentState.IDLE) {
            return Flux.error(new RuntimeException(
                    StrUtil.format("Cannot run agent from state: {}", this.getState())));
        }
        if (StrUtil.isBlank(userInput)) {
            return Flux.error(new RuntimeException("Cannot run agent with empty user prompt"));
        }

        return Flux.create(sink -> {
            try {
                // 设置状态
                this.setState(AgentState.RUNNING);
                this.setCurrentStep(0);

                // 添加用户输入到记忆
                getMemory().add(new UserMessage(userInput));
                trimMemoryIfNeeded();

                // 执行第一步
                int stepNumber = 1;
                this.setCurrentStep(stepNumber);
                log.info("Executing step {}/{} (stream mode)", stepNumber, getMaxSteps());

                // 准备提示词
                List<Message> messageList = getMemory();
                Prompt prompt = new Prompt(messageList, this.chatOptions);

                String effectiveSystemPrompt = getSystemPrompt();
                if (StrUtil.isNotBlank(getNextStepPrompt()) && getCurrentStep() > 1) {
                    effectiveSystemPrompt = StrUtil.format("{}\n\n{}",
                            getSystemPrompt(), getNextStepPrompt());
                }

                // 记录LLM调用开始时间
                long thinkStartTime = System.currentTimeMillis();

                // 先调用一次以检查是否有工具调用
                ChatResponse chatResponse = getChatClient().prompt(prompt)
                        .system(effectiveSystemPrompt)
                        .call()
                        .chatResponse();

                // 记录LLM调用耗时
                long thinkDuration = System.currentTimeMillis() - thinkStartTime;
                log.info("{} LLM思考耗时: {}ms", getName(), thinkDuration);

                this.toolCallChatResponse = chatResponse;
                AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
                List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

                // 输出提示信息
                String result = assistantMessage.getText();
                log.info("{}的思考：\n {}", getName(), result);
                log.info("{}选择了 {} 个工具来使用", getName(), CollUtil.size(toolCallList));

                if (CollUtil.isNotEmpty(toolCallList)) {
                    StringBuilder toolCallInfo = new StringBuilder("工具调用列表：\n");
                    for (int i = 0; i < toolCallList.size(); i++) {
                        AssistantMessage.ToolCall toolCall = toolCallList.get(i);
                        toolCallInfo.append(String.format("  [%d] %s - 参数: %s\n",
                                i + 1, toolCall.name(), toolCall.arguments()));
                    }
                    log.info(toolCallInfo.toString().trim());
                }

                // 如果没有工具调用，使用流式输出
                if (CollUtil.isEmpty(toolCallList)) {
                    setState(AgentState.FINISHED);
                    getMemory().add(assistantMessage);

                    // 使用流式调用获取响应
                    Flux<String> responseFlux = getChatClient().prompt(prompt)
                            .system(effectiveSystemPrompt)
                            .stream()
                            .content();

                    // 订阅流式响应
                    responseFlux.subscribe(
                            chunk -> sink.next(chunk),
                            error -> {
                                log.error("流式响应错误: {}", error.getMessage(), error);
                                setState(AgentState.ERROR);
                                sink.error(error);
                            },
                            () -> {
                                log.info("流式响应完成");
                                sink.complete();
                            }
                    );
                } else {
                    // 有工具调用，需要继续执行
                    // 记录助手消息（包含工具调用信息）
                    getMemory().add(assistantMessage);

                    // 执行工具调用
                    String actionResult = act();

                    // 检查是否完成
                    if (getState() == AgentState.FINISHED) {
                        // 任务完成，获取最终回答并使用流式输出
                        String finalAnswer = getLastAssistantMessage();
                        if (StrUtil.isNotBlank(finalAnswer)) {
                            // 将最终回答分块发送（模拟流式）
                            int chunkSize = 10;
                            for (int i = 0; i < finalAnswer.length(); i += chunkSize) {
                                int end = Math.min(i + chunkSize, finalAnswer.length());
                                sink.next(finalAnswer.substring(i, end));
                                // 添加小延迟，模拟流式效果
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                        sink.complete();
                    } else {
                        // 继续执行后续步骤（非流式）
                        continueExecution(sink, stepNumber + 1);
                    }
                }

            } catch (Exception e) {
                log.error("流式执行出错: {}", e.getMessage(), e);
                setState(AgentState.ERROR);
                sink.error(e);
            } finally {
                this.cleanup();
            }
        });
    }

    /**
     * 继续执行后续步骤（非流式，用于工具调用后的步骤）
     */
    private void continueExecution(reactor.core.publisher.FluxSink<String> sink, int startStep) {
        try {
            for (int i = startStep; i <= getMaxSteps() && getState() != AgentState.FINISHED; i++) {
                int stepNumber = i;
                setCurrentStep(stepNumber);
                log.info("Executing step {}/{}", stepNumber, getMaxSteps());

                // 验证消息序列
                validateMessageSequence();

                String stepResult = step();

                if (isStuck()) {
                    handleStuckState();
                }

                if (getState() == AgentState.FINISHED) {
                    log.info("执行完成！总步数: {}", stepNumber);
                    String finalAnswer = getLastAssistantMessage();
                    if (StrUtil.isNotBlank(finalAnswer)) {
                        // 将最终回答分块发送
                        int chunkSize = 10;
                        for (int j = 0; j < finalAnswer.length(); j += chunkSize) {
                            int end = Math.min(j + chunkSize, finalAnswer.length());
                            sink.next(finalAnswer.substring(j, end));
                        }
                    }
                    sink.complete();
                    return;
                }
            }

            // 检查是否超出步骤限制
            if (getCurrentStep() >= getMaxSteps() && getState() != AgentState.FINISHED) {
                setState(AgentState.FINISHED);
                String msg = StrUtil.format("Terminated: Reached max steps ({})", getMaxSteps());
                sink.next(msg);
                sink.complete();
            }
        } catch (Exception e) {
            log.error("继续执行时出错: {}", e.getMessage(), e);
            setState(AgentState.ERROR);
            sink.error(e);
        }
    }

    /**
     * 重置Agent状态
     */
    @Override
    public void reset() {
        super.reset();
        this.toolCallChatResponse = null;
        log.info("[{}]Agent 状态已重置", getName());
    }

    /**
     * 处理陷入循环的状态（重写父类方法，注入提示词）
     */
    @Override
    protected void handleStuckState() {
        String stuckPrompt = "观察到重复响应。考虑新策略，避免重复已尝试过的无效路径。";
        // 将提示词添加到nextStepPrompt中，下次think时会自动注入
        String currentPrompt = StrUtil.blankToDefault(this.nextStepPrompt, "");
        this.nextStepPrompt = StrUtil.format("{}\n{}", stuckPrompt, currentPrompt);
        log.warn("[{}] Agent检测到陷入循环状态，已注入提示词: {}", getName(), stuckPrompt);
    }
}