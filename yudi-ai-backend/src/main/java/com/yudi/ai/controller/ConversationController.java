package com.yudi.ai.controller;

import cn.hutool.core.util.StrUtil;
import com.yudi.ai.common.BaseResponse;
import com.yudi.ai.common.ErrorCode;
import com.yudi.ai.exception.BusinessException;
import com.yudi.ai.model.entity.User;
import com.yudi.ai.model.vo.ConversationListVO;
import com.yudi.ai.model.vo.ConversationMemoryVO;
import com.yudi.ai.service.ConversationMemoryService;
import com.yudi.ai.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话记忆管理控制器
 */
@Slf4j
@RestController
public class ConversationController {

    @Resource
    private ConversationMemoryService conversationMemoryService;

    /**
     * 获取当前用户的会话列表
     *
     * @return 会话列表
     */
    @GetMapping("/conversations")
    public BaseResponse<List<ConversationListVO>> getConversations() {
        User user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }

        List<ConversationListVO> conversations = conversationMemoryService.getConversationList(user.getId());
        return BaseResponse.success(conversations);
    }

    /**
     * 获取指定会话的历史记录
     *
     * @param conversationId 会话ID
     * @return 会话历史
     */
    @GetMapping("/conversation/history")
    public BaseResponse<List<ConversationMemoryVO>> getConversationHistory(@RequestParam("conversationId") String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            throw new BusinessException(ErrorCode.PARAMETER_NULL, "会话ID不能为空");
        }

        User user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }

        List<ConversationMemoryVO> history = conversationMemoryService.getConversationHistory(conversationId, user.getId());
        return BaseResponse.success(history);
    }

    /**
     * 删除指定会话
     *
     * @param conversationId 会话ID
     * @return 删除结果
     */
    @PostMapping("/conversation/delete")
    public BaseResponse<Boolean> deleteConversation(@RequestParam("conversationId") String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            throw new BusinessException(ErrorCode.PARAMETER_NULL, "会话ID不能为空");
        }

        User user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }

        boolean result = conversationMemoryService.deleteConversation(conversationId, user.getId());
        return BaseResponse.success("删除成功", result);
    }

    /**
     * 创建新会话
     *
     * @return 新会话ID
     */
    @PostMapping("/conversation/new")
    public BaseResponse<String> createConversation() {
        User user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }

        String conversationId = conversationMemoryService.createConversation(user.getId());
        return BaseResponse.success(conversationId);
    }
}

