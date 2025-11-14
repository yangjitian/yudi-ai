package com.yudi.ai.mapper;

import com.yudi.ai.model.entity.ConversationMemory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationMemoryMapper extends BaseMapper<ConversationMemory> {

    /**
     * 查询用户的会话列表（每个会话的最新更新时间）
     * @param userId 用户ID
     * @return 会话列表，包含conversationId和maxTime
     */
    List<Map<String, Object>> selectConversationListByUserId(@Param("userId") Long userId);

    /**
     * 查询指定会话的历史记录
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<ConversationMemory> selectHistoryByConversationId(@Param("conversationId") String conversationId, @Param("userId") Long userId);

    /**
     * 查询指定会话的最大轮次
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 最大轮次，如果没有记录返回0
     */
    Integer selectMaxRoundByConversationId(@Param("conversationId") String conversationId, @Param("userId") Long userId);

    /**
     * 删除指定会话的所有记录
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 删除的记录数
     */
    int deleteByConversationId(@Param("conversationId") String conversationId, @Param("userId") Long userId);
}




