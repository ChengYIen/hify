package com.hify.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.module.agent.controller.dto.ToolDefinitionResponse;
import com.hify.module.agent.repository.ToolDefinitionMapper;
import com.hify.module.agent.repository.entity.ToolDefinitionEntity;
import com.hify.module.agent.service.ToolDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具定义业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolDefinitionServiceImpl implements ToolDefinitionService {

    private final ToolDefinitionMapper toolDefinitionMapper;

    @Override
    public List<ToolDefinitionResponse> listEnabled() {
        List<ToolDefinitionEntity> entities = toolDefinitionMapper.selectList(
                new LambdaQueryWrapper<ToolDefinitionEntity>()
                        .eq(ToolDefinitionEntity::getStatus, "ENABLED")
                        .orderByAsc(ToolDefinitionEntity::getToolType)
                        .orderByAsc(ToolDefinitionEntity::getToolName));
        return entities.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private ToolDefinitionResponse toResponse(ToolDefinitionEntity entity) {
        return ToolDefinitionResponse.builder()
                .id(entity.getId())
                .toolName(entity.getToolName())
                .toolType(entity.getToolType())
                .description(entity.getDescription())
                .toolConfig(entity.getToolConfig())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
