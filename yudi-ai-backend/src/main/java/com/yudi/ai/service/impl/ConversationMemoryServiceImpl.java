package com.yudi.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.exception.BusinessException;
import com.yudi.ai.mapper.ConversationMemoryMapper;
import com.yudi.ai.model.entity.ConversationMemory;
import com.yudi.ai.model.vo.ConversationListVO;
import com.yudi.ai.model.vo.ConversationMemoryVO;
import com.yudi.ai.service.ConversationMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationMemoryServiceImpl extends ServiceImpl<ConversationMemoryMapper, ConversationMemory>
    implements ConversationMemoryService{

    @Resource
    private ConversationMemoryMapper conversationMemoryMapper;

    @Override
    public void saveConversationRound(String conversationId, Long userId, String userInput, String aiResponse) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            throw new BusinessException(ErrorCode.PARAMETER_NULL, "会话ID和用户ID不能为空");
        }

        // 查询当前会话的最大轮次
        Integer maxRound = conversationMemoryMapper.selectMaxRoundByConversationId(conversationId, userId);
        int nextRound = maxRound + 1;

        // 创建对话记录
        ConversationMemory memory = new ConversationMemory();
        memory.setConversation_id(conversationId);
        memory.setUserId(userId);
        memory.setConversationRound(nextRound);
        memory.setUserInput(userInput);
        memory.setAiResponse(aiResponse);
        memory.setConversationTime(LocalDateTime.now());

        boolean result = this.save(memory);
        if (result) {
            log.info("保存对话记录成功 - 会话ID: {}, 用户ID: {}, 轮次: {}", conversationId, userId, nextRound);
        } else {
            log.error("保存对话记录失败 - 会话ID: {}, 用户ID: {}", conversationId, userId);
        }
    }

    @Override
    public List<ConversationListVO> getConversationList(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMETER_NULL, "用户ID不能为空");
        }

        // 查询会话列表
        List<Map<String, Object>> list = conversationMemoryMapper.selectConversationListByUserId(userId);
        
        List<ConversationListVO> conversations = new ArrayList<>();
        if (CollUtil.isNotEmpty(list)) {
            for (Map<String, Object> item : list) {
                String conversationId = (String) item.get("conversationId");
                LocalDateTime maxTime = (LocalDateTime) item.get("maxTime");
                
                // 获取该会话的第一条用户消息作为标题
                String title = getConversationTitle(conversationId, userId);
                
                ConversationListVO vo = new ConversationListVO();
                vo.setConversationId(conversationId);
                vo.setTitle(title);
                vo.setUpdatedAt(maxTime);
                conversations.add(vo);
            }
        }

        return conversations;
    }

    @Override
    public List<ConversationMemoryVO> getConversationHistory(String conversationId, Long userId) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            throw new BusinessException(ErrorCode.PARAMETER_NULL, "会话ID和用户ID不能为空");
        }

        // 查询历史记录
        List<ConversationMemory> memories = conversationMemoryMapper.selectHistoryByConversationId(conversationId, userId);
        
        if (CollUtil.isEmpty(memories)) {
            return new ArrayList<>();
        }

        return memories.stream().map(memory -> {
            ConversationMemoryVO vo = new ConversationMemoryVO();
            vo.setUserInput(memory.getUserInput());
            vo.setAiResponse(memory.getAiResponse());
            vo.setConversationRound(memory.getConversationRound());
            vo.setConversationTime(memory.getConversationTime());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean deleteConversation(String conversationId, Long userId) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            throw new BusinessException(ErrorCode.PARAMETER_NULL, "会话ID和用户ID不能为空");
        }

        int deleted = conversationMemoryMapper.deleteByConversationId(conversationId, userId);
        if (deleted > 0) {
            log.info("删除会话成功 - 会话ID: {}, 用户ID: {}, 删除记录数: {}", conversationId, userId, deleted);
            return true;
        } else {
            log.warn("删除会话失败或会话不存在 - 会话ID: {}, 用户ID: {}", conversationId, userId);
            return false;
        }
    }

    @Override
    public String createConversation(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMETER_NULL, "用户ID不能为空");
        }

        // 生成UUID作为会话ID
        String conversationId = UUID.randomUUID().toString();
        log.info("创建新会话 - 会话ID: {}, 用户ID: {}", conversationId, userId);
        return conversationId;
    }

    /**
     * 获取会话标题（第一轮对话的用户消息前6个字符）
     */
    private String getConversationTitle(String conversationId, Long userId) {
        List<ConversationMemory> memories = conversationMemoryMapper.selectHistoryByConversationId(conversationId, userId);
        if (CollUtil.isEmpty(memories)) {
            return "新会话";
        }

        // 获取第一轮对话的用户输入
        ConversationMemory firstMemory = memories.get(0);
        String userInput = firstMemory.getUserInput();
        
        if (StrUtil.isBlank(userInput)) {
            return "新会话";
        }

        // 取前6个字符，超出用"...."拼接
        String trimmed = userInput.trim();
        if (trimmed.length() <= 6) {
            return trimmed;
        } else {
            return trimmed.substring(0, 6) + "....";
        }
    }
}




