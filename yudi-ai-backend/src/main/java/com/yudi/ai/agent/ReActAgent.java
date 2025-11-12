package com.yudi.ai.agent;

import com.yudi.ai.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 * <p>
 * ReAct模式流程：
 * 1. Think (思考): 分析当前情况，决定下一步行动
 * 2. Act (行动): 执行工具调用或生成响应
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考阶段：分析当前情况，决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    protected abstract boolean think();

    /**
     * 行动阶段：执行具体的操作（如工具调用、生成回答等）
     *
     * @return 行动的结果
     */
    protected abstract String act();

    /**
     * 执行单步操作（实现ReAct循环）
     * Think-Act 循环逻辑
     * @return 步骤执行结果
     */
    @Override
    protected String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // 检查状态，如果已经设置为 FINISHED，说明任务完成
                if (getState() == AgentState.FINISHED) {
                    // 从 memory 中获取最后的 AssistantMessage 内容作为回答
                    String finalAnswer = getLastAssistantMessage();
                    return finalAnswer != null ? finalAnswer : "任务完成";
                }
                return "思考完成 - 无需行动";
            }

            // 再行动
            String actionResult = act();
            // 检查状态，如果行动后状态变为 FINISHED，说明任务完成
            if (getState() == AgentState.FINISHED) {
                // 从 memory 中获取最后的 AssistantMessage 内容作为回答
                String finalAnswer = getLastAssistantMessage();
                return finalAnswer != null ? finalAnswer : actionResult;
            }
            return actionResult;
        } catch (Exception e) {
            log.error("步骤执行失败: {}",e.getMessage(), e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 从 memory 中获取最后的 AssistantMessage 内容
     * @return 最后的助手消息内容，如果没有则返回 null
     */
    protected String getLastAssistantMessage() {
        List<Message> messages = getMemory();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        
        // 从后往前查找最后一个 AssistantMessage
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof AssistantMessage) {
                AssistantMessage assistantMessage = (AssistantMessage) message;
                String content = assistantMessage.getText();
                if (content != null && !content.trim().isEmpty()) {
                    return content;
                }
            }
        }
        return null;
    }


    /**
     * 重置Agent状态
     */
    @Override
    public void reset() {
        super.reset();
    }
}
