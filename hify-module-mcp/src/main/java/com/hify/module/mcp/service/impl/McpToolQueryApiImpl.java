package com.hify.module.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.McpToolMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.shared.tool.McpToolQueryApi;
import com.hify.shared.tool.dto.AgentBoundToolDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 工具查询契约实现.
 */
@Service
@RequiredArgsConstructor
public class McpToolQueryApiImpl implements McpToolQueryApi {

    private final McpToolMapper mcpToolMapper;
    private final McpServerMapper mcpServerMapper;

    @Override
    public List<Long> listAvailableToolIds(Collection<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        List<McpToolEntity> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpToolEntity>()
                        .in(McpToolEntity::getId, toolIds));
        if (tools.isEmpty()) {
            return List.of();
        }

        List<Long> serverIds = tools.stream()
                .map(McpToolEntity::getMcpServerId)
                .distinct()
                .toList();
        Set<Long> enabledServerIds = mcpServerMapper.selectList(
                        new LambdaQueryWrapper<McpServerEntity>()
                                .in(McpServerEntity::getId, serverIds))
                .stream()
                .filter(server -> "ENABLED".equals(server.getStatus()))
                .map(McpServerEntity::getId)
                .collect(Collectors.toSet());

        return tools.stream()
                .filter(tool -> enabledServerIds.contains(tool.getMcpServerId()))
                .map(McpToolEntity::getId)
                .toList();
    }

    @Override
    public List<AgentBoundToolDTO> listBoundTools(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return mcpToolMapper.selectBoundTools(agentId);
    }
}
