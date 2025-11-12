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
        // 注意：即使禁用了内部工具执行，ToolCallingManager仍然可以执行工具
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        // 优化：添加性能相关参数
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .withTemperature(0.3)  // 降低temperature提高响应速度和确定性
                .withMaxToken(2000)    // 限制最大token数，加快响应
                .withTopP(0.8)         // 稍微提高TopP以保持一定创造性
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词（优化：不每次都添加到memory，而是合并到system prompt）
        // 注意：nextStepPrompt仍然需要，但可以通过更高效的方式注入
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            // 只在第一次或需要时添加，避免重复累积
            // 这里简化处理，因为system prompt已经包含了效率提示
            // 如果需要动态注入，可以考虑通过system prompt参数传递
        }
        // 2、调用 AI 大模型，获取工具调用结果
        // 参考 CookController 的实现方式：工具已在 ChatClient 构建时注册，无需再次传递
        List<Message> messageList = getMemory();
        try {
            // 添加调试日志，检查工具注册情况
            log.debug("可用工具数量 - ToolCallback: {}, 本地工具: {}", 
                    availableTools != null ? availableTools.length : 0,
                    localToolInstances != null ? localToolInstances.length : 0);
            log.debug("当前消息数量: {}", messageList.size());
            
            // 参考 CookController 的实现方式：直接使用 Prompt 对象传递消息
            // 工具已经在 ChatClient 构建时通过 .defaultTools() 和 .defaultToolCallbacks() 注册
            // Spring AI 会自动处理工具调用，无需手动传递工具
            Prompt prompt = new Prompt(messageList, this.chatOptions);
            
            // 记录LLM调用开始时间
            long thinkStartTime = System.currentTimeMillis();
            
            // 限制memory长度，避免上下文过长
            trimMemoryIfNeeded();

            // 如果nextStepPrompt有值，可以合并到system prompt中
            String effectiveSystemPrompt = getSystemPrompt();
            if (StrUtil.isNotBlank(getNextStepPrompt()) && getCurrentStep() > 1) {
                // 只在非第一步时注入nextStepPrompt，避免重复
                effectiveSystemPrompt = getSystemPrompt() + "\n\n" + getNextStepPrompt();
            }
            
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(effectiveSystemPrompt)  // 如果为空，不会覆盖默认的系统提示词
                    .call()
                    .chatResponse();
            
            // 记录LLM调用耗时
            long thinkDuration = System.currentTimeMillis() - thinkStartTime;
            log.info("{} LLM思考耗时: {}ms", getName(), thinkDuration);
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info("{}的思考：\n {}", getName(), result);
            log.info("{}选择了 {} 个工具来使用", getName(), toolCallList.size());
            if (!toolCallList.isEmpty()) {
                StringBuilder toolCallInfo = new StringBuilder("工具调用列表：\n");
                for (int i = 0; i < toolCallList.size(); i++) {
                    AssistantMessage.ToolCall toolCall = toolCallList.get(i);
                    toolCallInfo.append(String.format("  [%d] %s - 参数: %s\n", 
                            i + 1, toolCall.name(), toolCall.arguments()));
                }
                log.info(toolCallInfo.toString().trim());
            }
            
            // 检查文本中是否包含terminate调用（作为后备方案）
            // 如果LLM在文本中提到了terminate但工具调用未正确解析
            boolean hasTerminateInText = false;
            if (result != null && toolCallList.isEmpty()) {
                // 检查是否包含terminate相关的function_call JSON格式
                String lowerResult = result.toLowerCase();
                // 匹配多种可能的terminate调用模式
                hasTerminateInText = (lowerResult.contains("\"name\":\"terminate\"") || 
                                     lowerResult.contains("\"name\": \"terminate\"") ||
                                     (lowerResult.contains("function_call") && lowerResult.contains("terminate"))) &&
                                    (lowerResult.contains("\"name\"") || lowerResult.contains("name"));
            }
            
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 检查文本中是否明确表示要终止（作为后备检测）
                if (hasTerminateInText) {
                    log.warn("检测到文本中包含terminate调用，但工具调用解析失败。直接终止任务。");
                    setState(AgentState.FINISHED);
                    // 记录助手消息
                    getMemory().add(assistantMessage);
                    return false;
                }
                
                // 判断是否应该结束任务
                // 如果LLM给出了完整回答且没有调用工具，说明任务已完成
                // 特别地，如果这是第一步（currentStep == 1），说明LLM已经给出了完整回答，应该结束
                boolean shouldFinish = false;
                
                // 策略1: 如果是第一步且没有工具调用，说明已经给出了完整回答
                if (getCurrentStep() == 1) {
                    shouldFinish = true;
                    log.info("第一步完成，LLM已给出完整回答且无需工具调用，任务完成");
                } 
                // 策略2: 如果回答看起来完整（包含明确的结束信号或建议用户选择）
                else if (result != null && result.length() > 50) {
                    // 检查回答中是否包含建议性的问句（如"要不要"、"是否"等），这表明LLM在等待用户响应
                    // 这种情况下，不应该自动执行工具，而是等待用户明确要求
                    String lowerResult = result.toLowerCase();
                    boolean hasSuggestiveQuestion = lowerResult.contains("要不要") || 
                                                   lowerResult.contains("是否") ||
                                                   lowerResult.contains("要不要我") ||
                                                   lowerResult.contains("需要我") ||
                                                   lowerResult.contains("只需说");
                    
                    // 如果包含建议性问句，说明LLM在询问用户，这是最终回答，应该结束
                    // 如果不包含建议性问句，说明可能是中间步骤，需要继续
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
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error("{}的思考过程遇到了问题：{}", getName(), e.getMessage());
            getMemory().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
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
        int toolCallCount = toolCallChatResponse.getResult().getOutput().getToolCalls().size();
        log.info("{} 开始执行 {} 个工具调用", getName(), toolCallCount);
        
        // 调用工具
        Prompt prompt = new Prompt(getMemory(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        
            // 记录工具调用耗时
        long actDuration = System.currentTimeMillis() - actStartTime;
        log.info("{} 工具执行完成，耗时: {}ms (平均每个工具: {}ms)", 
                getName(), actDuration, toolCallCount > 0 ? actDuration / toolCallCount : 0);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMemory(toolExecutionResult.conversationHistory());
        // 限制memory长度，避免上下文过长
        trimMemoryIfNeeded();
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具（支持多种可能的工具名称）
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> {
                    String toolName = response.name();
                    return "doTerminate".equals(toolName) || 
                           "terminate".equals(toolName) ||
                           toolName.toLowerCase().contains("terminate");
                });
        if (terminateToolCalled) {
            // 任务结束，更改状态
            log.info("检测到终止工具调用，任务结束");
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        return results;
    }

    /**
     * 流式执行Agent（返回Flux<String>）
     * 当第一步就完成时（没有工具调用），使用流式输出
     * 如果需要多步执行，则先执行完所有步骤，最后一步使用流式输出
     * 
     * @param userInput 用户输入
     * @return 流式响应
     */
    public Flux<String> runStream(String userInput) {
        // 基础校验
        if (this.getState() != AgentState.IDLE) {
            return Flux.error(new RuntimeException("Cannot run agent from state: " + this.getState()));
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
                    effectiveSystemPrompt = getSystemPrompt() + "\n\n" + getNextStepPrompt();
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
                log.info("{}选择了 {} 个工具来使用", getName(), toolCallList.size());
                if (!toolCallList.isEmpty()) {
                    StringBuilder toolCallInfo = new StringBuilder("工具调用列表：\n");
                    for (int i = 0; i < toolCallList.size(); i++) {
                        AssistantMessage.ToolCall toolCall = toolCallList.get(i);
                        toolCallInfo.append(String.format("  [%d] %s - 参数: %s\n", 
                                i + 1, toolCall.name(), toolCall.arguments()));
                    }
                    log.info(toolCallInfo.toString().trim());
                }
                
                // 如果没有工具调用，使用流式输出
                if (toolCallList.isEmpty()) {
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
                        if (finalAnswer != null && !finalAnswer.isEmpty()) {
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
                
                String stepResult = step();
                
                if (isStuck()) {
                    handleStuckState();
                }
                
                if (getState() == AgentState.FINISHED) {
                    log.info("执行完成！总步数: {}", stepNumber);
                    String finalAnswer = getLastAssistantMessage();
                    if (finalAnswer != null && !finalAnswer.isEmpty()) {
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
                sink.next("Terminated: Reached max steps (" + getMaxSteps() + ")");
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
        String currentPrompt = this.nextStepPrompt != null ? this.nextStepPrompt : "";
        this.nextStepPrompt = stuckPrompt + "\n" + currentPrompt;
        log.warn("[{}] Agent检测到陷入循环状态，已注入提示词: {}", getName(), stuckPrompt);
    }
}
