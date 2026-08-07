package com.hify.module.conversation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.conversation.repository.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper.
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
