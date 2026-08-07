package com.hify.module.mcp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.mcp.controller.dto.McpServerCreateRequest;
import com.hify.module.mcp.controller.dto.McpServerResponse;
import com.hify.module.mcp.controller.dto.McpServerUpdateRequest;

/**
 * MCP 服务配置业务接口.
 */
public interface McpServerService {

    IPage<McpServerResponse> page(int page, int pageSize);

    McpServerResponse getById(Long id);

    McpServerResponse create(McpServerCreateRequest request);

    McpServerResponse update(Long id, McpServerUpdateRequest request);

    McpServerResponse enable(Long id);

    McpServerResponse disable(Long id);

    void delete(Long id);
}
