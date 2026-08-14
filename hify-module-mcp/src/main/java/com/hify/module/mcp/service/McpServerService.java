package com.hify.module.mcp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.mcp.controller.dto.McpServerCreateRequest;
import com.hify.module.mcp.controller.dto.McpServerResponse;
import com.hify.module.mcp.controller.dto.McpServerUpdateRequest;

/**
 * MCP Server 管理业务接口.
 */
public interface McpServerService {

    IPage<McpServerResponse> page(int page, int pageSize);

    McpServerResponse getById(Long id);

    McpServerResponse create(McpServerCreateRequest request);

    McpServerResponse update(Long id, McpServerUpdateRequest request);

    void delete(Long id);
}
