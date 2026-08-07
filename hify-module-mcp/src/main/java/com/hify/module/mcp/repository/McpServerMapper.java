package com.hify.module.mcp.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 服务配置 Mapper.
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerEntity> {
}
