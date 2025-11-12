package com.yudi.ai.controller;

import cn.hutool.core.io.resource.ResourceUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.jdbc.MysqlChatMemoryRepository;
import com.yudi.ai.chatmemory.FileBasedChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cook_memory")
public class CookMemoryController {

    private static final String DEFAULT_PROMPT = ResourceUtil.readUtf8Str("prompts/cook_app_system_prompt.md");

    private final ChatClient dashScopeChatClient;
    private final ChatMemory chatMemory;

    public CookMemoryController(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate, ChatClient.Builder chatClientBuilder) {
        
        // 基于文件存储记忆
//        String fileDir = System.getProperty("user.dir") + "/temp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        
        // 构造 ChatMemoryRepository 和 ChatMemory
        ChatMemoryRepository chatMemoryRepository = MysqlChatMemoryRepository.mysqlBuilder()
                .jdbcTemplate(jdbcTemplate)
                .build();
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
        this.dashScopeChatClient = chatClientBuilder
                .defaultSystem(DEFAULT_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 注册Advisor
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.7)
                                .build()
                )
                .build();
    }

    /**
     * 简单聊天接口
     * 使用 DashScope 聊天客户端进行基础对话，支持对话记忆功能
     *
     * @param query 用户输入的值
     * @param chatId 对话ID，相同的chatId会共享对话历史
     * @return AI模型的文本回复内容
     */
    @GetMapping("/simple/chat")
     public String simpleChat(@RequestParam(value = "query")String query,
                             @RequestParam(value = "chat-id") String chatId) {

        return dashScopeChatClient.prompt(query)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call().content();
    }
    
    /**
     * 调试接口：获取指定对话的记忆内容
     */
    @GetMapping("/debug/memory")
    public String debugMemory(@RequestParam(value = "chat-id") String chatId) {
        List<Message> messages = chatMemory.get(chatId);
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("对话ID: ").append(chatId).append("\n");
        debugInfo.append("消息数量: ").append(messages.size()).append("\n\n");
        
        if (messages.isEmpty()) {
            debugInfo.append("无历史对话记录");
        } else {
            debugInfo.append("历史对话内容:\n");
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                debugInfo.append("[").append(i + 1).append("] ")
                         .append(msg.getClass().getSimpleName())
                         .append(": ")
                         .append(msg.getText())
                         .append("\n");
                log.info("  [{}] {}: {}", i + 1, msg.getClass().getSimpleName(), 
                    msg.getText().length() > 100 ? msg.getText().substring(0, 100) + "..." : msg.getText());
            }
        }
        
        return debugInfo.toString();
    }
    
    /**
     * 调试接口：清除指定对话的记忆
     */
    @GetMapping("/debug/clear")
    public String clearMemory(@RequestParam(value = "chat-id") String chatId) {
        List<Message> beforeMessages = chatMemory.get(chatId);
        chatMemory.clear(chatId);
        List<Message> afterMessages = chatMemory.get(chatId);
        return String.format("记忆已清除 - 对话ID: %s, 清除前: %d条消息, 清除后: %d条消息", 
                            chatId, beforeMessages.size(), afterMessages.size());
    }
}
