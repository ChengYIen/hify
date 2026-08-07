package com.hify.module.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.mcp.controller.dto.McpServerCreateRequest;
import com.hify.module.mcp.controller.dto.McpServerResponse;
import com.hify.module.mcp.controller.dto.McpServerUpdateRequest;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MCP 服务配置业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;

    @Override
    public IPage<McpServerResponse> page(int page, int pageSize) {
        Page<McpServerEntity> p = new Page<>(page, pageSize);
        Page<McpServerEntity> result = mcpServerMapper.selectPage(p,
                new LambdaQueryWrapper<McpServerEntity>()
                        .orderByDesc(McpServerEntity::getId));
        return result.convert(this::toResponse);
    }

    @Override
    public McpServerResponse getById(Long id) {
        McpServerEntity entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 服务不存在: id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse create(McpServerCreateRequest request) {
        McpServerEntity entity = new McpServerEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setTransport(request.getTransport());
        entity.setCommand(request.getCommand());
        entity.setArgs(request.getArgs());
        entity.setEnvVars(request.getEnvVars());
        entity.setUrl(request.getUrl());
        entity.setHeaders(request.getHeaders());
        entity.setTimeoutMs(request.getTimeoutMs());
        entity.setStatus("ENABLED");
        mcpServerMapper.insert(entity);
        log.info("McpServer 创建成功: id={}, name={}", entity.getId(), entity.getName());
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse update(Long id, McpServerUpdateRequest request) {
        McpServerEntity entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 服务不存在: id=" + id);
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getTransport() != null) {
            entity.setTransport(request.getTransport());
        }
        if (request.getCommand() != null) {
            entity.setCommand(request.getCommand());
        }
        if (request.getArgs() != null) {
            entity.setArgs(request.getArgs());
        }
        if (request.getEnvVars() != null) {
            entity.setEnvVars(request.getEnvVars());
        }
        if (request.getUrl() != null) {
            entity.setUrl(request.getUrl());
        }
        if (request.getHeaders() != null) {
            entity.setHeaders(request.getHeaders());
        }
        if (request.getTimeoutMs() != null) {
            entity.setTimeoutMs(request.getTimeoutMs());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        mcpServerMapper.updateById(entity);
        log.info("McpServer 更新成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse enable(Long id) {
        McpServerEntity entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 服务不存在: id=" + id);
        }
        entity.setStatus("ENABLED");
        mcpServerMapper.updateById(entity);
        log.info("McpServer 启用: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse disable(Long id) {
        McpServerEntity entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 服务不存在: id=" + id);
        }
        entity.setStatus("DISABLED");
        mcpServerMapper.updateById(entity);
        log.info("McpServer 禁用: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        McpServerEntity entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 服务不存在: id=" + id);
        }
        mcpServerMapper.deleteById(id);
        log.info("McpServer 删除成功: id={}", id);
    }

    private McpServerResponse toResponse(McpServerEntity entity) {
        return McpServerResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .transport(entity.getTransport())
                .command(entity.getCommand())
                .args(entity.getArgs())
                .envVars(entity.getEnvVars())
                .url(entity.getUrl())
                .headers(entity.getHeaders())
                .timeoutMs(entity.getTimeoutMs())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
