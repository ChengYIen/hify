package com.hify.module.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.mcp.controller.dto.ConnectionTestResult;
import com.hify.module.mcp.controller.dto.McpToolResponse;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.McpToolMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.module.mcp.service.McpClientService;
import com.hify.module.mcp.service.McpConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MCP Server 连通性测试实现，成功后把工具列表同步到 hify_mcp_tool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectivityServiceImpl implements McpConnectivityService {

    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final McpClientService mcpClientService;

    @Override
    public ConnectionTestResult testConnection(Long mcpServerId) {
        McpServerEntity server = mcpServerMapper.selectById(mcpServerId);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "id=" + mcpServerId);
        }

        long start = System.currentTimeMillis();
        try {
            List<McpToolResponse> tools = mcpClientService.listToolResponses(mcpServerId);
            replaceTools(mcpServerId, tools);
            return ConnectionTestResult.builder()
                    .success(true)
                    .latencyMs(System.currentTimeMillis() - start)
                    .toolCount(tools.size())
                    .build();
        } catch (RuntimeException e) {
            log.warn("MCP Server 连通性测试失败: id={}, error={}", mcpServerId, e.getMessage());
            return ConnectionTestResult.builder()
                    .success(false)
                    .latencyMs(System.currentTimeMillis() - start)
                    .toolCount(0)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private void replaceTools(Long mcpServerId, List<McpToolResponse> tools) {
        List<McpToolEntity> existing = mcpToolMapper.selectAllByServerId(mcpServerId);
        Map<String, McpToolEntity> existingByName = new HashMap<>();
        for (McpToolEntity entity : existing) {
            existingByName.put(entity.getToolName(), entity);
        }

        Set<String> keptNames = new HashSet<>();
        for (McpToolResponse tool : tools) {
            McpToolEntity entity = existingByName.get(tool.getToolName());
            if (entity == null) {
                entity = new McpToolEntity();
                entity.setMcpServerId(mcpServerId);
                entity.setToolName(tool.getToolName());
                entity.setDescription(tool.getDescription());
                entity.setInputSchema(tool.getInputSchema());
                mcpToolMapper.insert(entity);
            } else {
                entity.setDescription(tool.getDescription());
                entity.setInputSchema(tool.getInputSchema());
                mcpToolMapper.restoreAndUpdate(entity);
            }
            keptNames.add(tool.getToolName());
        }

        for (McpToolEntity entity : existing) {
            if (!keptNames.contains(entity.getToolName())
                    && entity.getDeleted() != null
                    && entity.getDeleted() == 0) {
                mcpToolMapper.deleteById(entity.getId());
            }
        }
    }
}
