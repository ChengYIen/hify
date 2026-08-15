package com.hify.module.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.mcp.controller.dto.McpServerCreateRequest;
import com.hify.module.mcp.controller.dto.McpServerResponse;
import com.hify.module.mcp.controller.dto.McpServerUpdateRequest;
import com.hify.module.mcp.controller.dto.McpToolResponse;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.McpToolMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.module.mcp.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Server 管理业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private static final String TRANSPORT_STREAMABLE = "streamable";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;

    @Override
    public IPage<McpServerResponse> page(int page, int pageSize) {
        Page<McpServerEntity> p = new Page<>(page, pageSize);
        Page<McpServerEntity> result = mcpServerMapper.selectPage(p,
                new LambdaQueryWrapper<McpServerEntity>().orderByDesc(McpServerEntity::getId));

        List<Long> serverIds = result.getRecords().stream()
                .map(McpServerEntity::getId)
                .collect(Collectors.toList());
        Map<Long, Long> toolCountMap = Collections.emptyMap();
        if (!serverIds.isEmpty()) {
            List<McpToolEntity> tools = mcpToolMapper.selectList(
                    new LambdaQueryWrapper<McpToolEntity>()
                            .in(McpToolEntity::getMcpServerId, serverIds));
            toolCountMap = tools.stream().collect(
                    Collectors.groupingBy(McpToolEntity::getMcpServerId, Collectors.counting()));
        }
        Map<Long, Long> finalToolCountMap = toolCountMap;

        return result.convert(entity -> toResponse(entity,
                finalToolCountMap.getOrDefault(entity.getId(), 0L).intValue()));
    }

    @Override
    public McpServerResponse getById(Long id) {
        McpServerEntity entity = requireEntity(id);
        List<McpToolResponse> tools = mcpToolMapper.selectList(
                        new LambdaQueryWrapper<McpToolEntity>()
                                .eq(McpToolEntity::getMcpServerId, id)
                                .orderByAsc(McpToolEntity::getToolName))
                .stream()
                .map(this::toToolResponse)
                .toList();
        return toResponse(entity, tools.size(), tools);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse create(McpServerCreateRequest request) {
        Long count = mcpServerMapper.selectCount(
                new LambdaQueryWrapper<McpServerEntity>()
                        .eq(McpServerEntity::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "MCP Server 名称已存在: " + request.getName());
        }

        boolean enabled = request.getEnabled() == null || request.getEnabled();
        McpServerEntity entity = new McpServerEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setUrl(request.getEndpoint());
        entity.setTransport(TRANSPORT_STREAMABLE);
        entity.setTimeoutMs(DEFAULT_TIMEOUT_MS);
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        mcpServerMapper.insert(entity);
        log.info("McpServer 创建成功: id={}, name={}", entity.getId(), entity.getName());
        return toResponse(entity, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse update(Long id, McpServerUpdateRequest request) {
        McpServerEntity entity = requireEntity(id);
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(entity.getName())) {
            Long count = mcpServerMapper.selectCount(
                    new LambdaQueryWrapper<McpServerEntity>()
                            .eq(McpServerEntity::getName, request.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.DUPLICATE, "MCP Server 名称已存在: " + request.getName());
            }
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getEndpoint())) {
            entity.setUrl(request.getEndpoint());
        }
        if (request.getEnabled() != null) {
            entity.setStatus(Boolean.TRUE.equals(request.getEnabled()) ? "ENABLED" : "DISABLED");
        }
        mcpServerMapper.updateById(entity);
        log.info("McpServer 更新成功: id={}", id);
        return toResponse(entity, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireEntity(id);
        long boundCount = mcpToolMapper.countAgentBindings(id);
        if (boundCount > 0) {
            throw new BizException(ErrorCode.DATA_CONFLICT,
                    "仍有 Agent 绑定该 MCP Server 的工具，无法删除，绑定数=" + boundCount);
        }
        mcpToolMapper.delete(new LambdaQueryWrapper<McpToolEntity>()
                .eq(McpToolEntity::getMcpServerId, id));
        mcpServerMapper.deleteById(id);
        log.info("McpServer 删除成功: id={}", id);
    }

    private McpServerEntity requireEntity(Long id) {
        McpServerEntity entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "id=" + id);
        }
        return entity;
    }

    private McpToolResponse toToolResponse(McpToolEntity entity) {
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

    private McpServerResponse toResponse(McpServerEntity entity, int toolCount) {
        return toResponse(entity, toolCount, null);
    }

    private McpServerResponse toResponse(McpServerEntity entity, int toolCount, List<McpToolResponse> tools) {
        return McpServerResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .endpoint(entity.getUrl())
                .enabled("ENABLED".equals(entity.getStatus()))
                .toolCount(toolCount)
                .tools(tools)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
