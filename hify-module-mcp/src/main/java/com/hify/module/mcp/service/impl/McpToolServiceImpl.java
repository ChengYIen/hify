package com.hify.module.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.module.mcp.controller.dto.McpToolResponse;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.McpToolMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.module.mcp.service.McpToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 工具查询业务实现.
 */
@Service
@RequiredArgsConstructor
public class McpToolServiceImpl implements McpToolService {

    private final McpToolMapper mcpToolMapper;
    private final McpServerMapper mcpServerMapper;

    @Override
    public List<McpToolResponse> listAllEnabled() {
        List<McpToolEntity> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpToolEntity>()
                        .orderByAsc(McpToolEntity::getMcpServerId)
                        .orderByAsc(McpToolEntity::getToolName));
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
                .map(this::toResponse)
                .toList();
    }

    private McpToolResponse toResponse(McpToolEntity entity) {
        return McpToolResponse.builder()
                .id(entity.getId())
                .mcpServerId(entity.getMcpServerId())
                .toolName(entity.getToolName())
                .description(entity.getDescription())
                .inputSchema(entity.getInputSchema())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
