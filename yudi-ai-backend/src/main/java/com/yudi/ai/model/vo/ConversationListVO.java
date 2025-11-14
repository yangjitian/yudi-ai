package com.yudi.ai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项VO
 */
@Data
public class ConversationListVO {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 会话标题
     */
    private String title;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
}


