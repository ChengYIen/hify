package com.hify.module.agent.service;

import com.hify.module.agent.controller.dto.ToolDefinitionResponse;

import java.util.List;

/**
 * 工具定义业务接口.
 */
public interface ToolDefinitionService {

    /**
     * 列出所有已启用的工具定义.
     *
     * @return 已启用的工具定义列表
     */
    List<ToolDefinitionResponse> listEnabled();
}
