package com.yudi.ai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话记录VO
 */
@Data
public class ConversationMemoryVO {
    
    /**
     * 用户输入内容
     */
    private String userInput;
    
    /**
     * AI回复内容
     */
    private String aiResponse;
    
    /**
     * 对话轮次
     */
    private Integer conversationRound;
    
    /**
     * 对话时间
     */
    private LocalDateTime conversationTime;
}


