package com.yudi.ai.agent;

import cn.hutool.core.util.StrUtil;
import com.yudi.ai.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Slf4j
@Data
public abstract class BaseAgent {

    private String name;    // Agent的名称
    private AgentState state = AgentState.IDLE;    // 当前执行状态
    private int maxSteps = 10;  // 减少默认步数，提高响应速度
    private int currentStep = 0;
    private List<Message> memory = new ArrayList<>();

    // 循环检测阈值：当检测到重复响应次数达到此值时，认为陷入循环
    private int duplicateThreshold = 2;

    // 限制memory最大长度，避免上下文过长导致响应变慢
    private static final int MAX_MEMORY_SIZE = 20;

    /**
     * 运行Agent的主循环
     * <p>
     * @param userInput 用户输入
     * @return Agent的最终响应
     */
    public String run(String userInput) {
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userInput)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        this.currentStep = 0;

        // 添加用户输入到记忆
        memory.add(new UserMessage(userInput));
        // 限制memory长度，避免上下文过长
        trimMemoryIfNeeded();

        // 保存结果列表
        List<String> results = new ArrayList<>();

        try {
            // 执行循环，直到完成或达到最大步数
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 执行单步操作
                String stepResult = step();

                // 每一步执行完后检查是否陷入循环
                if (isStuck()) {
                    handleStuckState();
                }

                if (stepResult != null && !stepResult.isEmpty()) {
                    // 如果步骤返回了结果，可能任务完成，但需要检查状态
                    if (state == AgentState.FINISHED) {
                        log.info("执行完成！总步数: {}", stepNumber);
                        // 任务完成时，直接返回最终的AI回答内容，不添加步骤前缀
                        return stepResult;
                    }
                    // 未完成时，保留步骤信息用于调试
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                }
            }

            // 检查是否超出步骤限制
            if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
                log.warn("达到最大步数 {} 仍未完成", maxSteps);
            }

            return String.join("\n", results);

        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("执行出错: {}", e.getMessage(), e);
            return "执行错误: " + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    /**
     * 执行单步操作（抽象方法，由子类实现）
     *
     * @return 步骤执行结果，如果返回非空字符串，表示步骤执行有结果；
     *         子类需要根据业务逻辑决定是否设置状态为 FINISHED
     */
    protected abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }

    /**
     * 重置Agent状态
     */
    public void reset() {
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.memory.clear();
        log.info("[{}] 重置状态", name);
    }

    /**
     * 检查代理是否陷入循环
     *
     * @return 是否陷入循环
     */
    protected boolean isStuck() {
        List<Message> messages = this.memory;
        if (messages.size() < 2) {
            return false;
        }

        Message lastMessage = messages.get(messages.size() - 1);
        // 只检查助手消息的重复
        if (!(lastMessage instanceof AssistantMessage)) {
            return false;
        }

        String lastContent = lastMessage.getText();
        if (lastContent == null || lastContent.isEmpty()) {
            return false;
        }

        // 计算重复内容出现次数
        int duplicateCount = 0;
        for (int i = messages.size() - 2; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage) {
                String content = msg.getText();
                if (content != null && lastContent.equals(content)) {
                    duplicateCount++;
                }
            }
        }

        return duplicateCount >= this.duplicateThreshold;
    }

    /**
     * 处理陷入循环的状态
     */
    protected void handleStuckState() {
        String stuckPrompt = "观察到重复响应。考虑新策略，避免重复已尝试过的无效路径。";
        // 如果子类有nextStepPrompt字段，需要在该类中处理
        // 这里仅记录日志，子类可以重写此方法来自定义处理逻辑
        log.warn("[{}] Agent检测到陷入循环状态，重复阈值: {}", getName(), duplicateThreshold);
        log.warn("建议: {}", stuckPrompt);
    }

    /**
     * 限制memory长度，保留最近的对话历史
     * 保留策略：保留第一个用户消息和最近的N条消息
     */
    protected void trimMemoryIfNeeded() {
        if (memory.size() > MAX_MEMORY_SIZE) {
            int originalSize = memory.size();
            // 保留第一个消息（通常是用户初始输入）
            Message firstMessage = memory.get(0);
            // 保留最近的N-1条消息（保留第一个）
            int keepCount = MAX_MEMORY_SIZE - 1;
            List<Message> recentMessages = memory.subList(memory.size() - keepCount, memory.size());

            memory.clear();
            memory.add(firstMessage);
            memory.addAll(recentMessages);

            log.debug("[{}] Memory已修剪，从 {} 条消息减少到 {} 条", getName(), originalSize, memory.size());
        }
    }
}