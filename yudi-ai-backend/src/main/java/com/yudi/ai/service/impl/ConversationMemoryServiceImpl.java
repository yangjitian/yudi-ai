package com.yudi.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.ai.model.entity.ConversationMemory;
import com.yudi.ai.service.ConversationMemoryService;
import com.yudi.ai.mapper.ConversationMemoryMapper;
import org.springframework.stereotype.Service;


@Service
public class ConversationMemoryServiceImpl extends ServiceImpl<ConversationMemoryMapper, ConversationMemory>
    implements ConversationMemoryService{

}




