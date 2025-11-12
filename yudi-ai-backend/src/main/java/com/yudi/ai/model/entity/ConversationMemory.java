package com.yudi.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * 对话记忆表
 * @TableName conversation_memory
 */
@TableName(value ="conversation_memory")
@Data
public class ConversationMemory implements Serializable {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID（UUID）
     */
    private String conversation_id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 对话轮次
     */
    private Integer conversationRound;

    /**
     * 用户输入内容
     */
    private String userInput;

    /**
     * AI回复内容
     */
    private String aiResponse;

    /**
     * 对话时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime conversationTime;

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}