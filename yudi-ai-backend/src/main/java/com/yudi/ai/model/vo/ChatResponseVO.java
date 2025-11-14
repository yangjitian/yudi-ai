package com.yudi.ai.model.vo;

import lombok.Data;

/**
 * 聊天响应VO
 */
@Data
public class ChatResponseVO {
    
    /**
     * AI回答
     */
    private String answer;
    
    /**
     * 会话ID
     */
    private String conversationId;
}


