package com.hify.module.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.agent.repository.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper.
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}
