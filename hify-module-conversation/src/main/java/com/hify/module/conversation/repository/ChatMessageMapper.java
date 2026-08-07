package com.hify.module.conversation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.conversation.repository.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper.
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
