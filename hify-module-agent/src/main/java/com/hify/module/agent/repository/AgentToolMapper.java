package com.hify.module.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.agent.repository.entity.AgentToolEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 工具关联 Mapper.
 */
@Mapper
public interface AgentToolMapper extends BaseMapper<AgentToolEntity> {
}
