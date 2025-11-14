package com.yudi.ai.service;

import com.yudi.ai.model.entity.ConversationMemory;
import com.yudi.ai.model.vo.ConversationListVO;
import com.yudi.ai.model.vo.ConversationMemoryVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ConversationMemoryService extends IService<ConversationMemory> {

    /**
     * 保存一轮对话
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param userInput      用户输入
     * @param aiResponse     AI回复
     */
    void saveConversationRound(String conversationId, Long userId, String userInput, String aiResponse);

    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ConversationListVO> getConversationList(Long userId);

    /**
     * 获取指定会话的历史记录
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 会话历史
     */
    List<ConversationMemoryVO> getConversationHistory(String conversationId, Long userId);

    /**
     * 删除指定会话
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteConversation(String conversationId, Long userId);

    /**
     * 创建新会话
     * @param userId 用户ID
     * @return 会话ID
     */
    String createConversation(Long userId);
}
