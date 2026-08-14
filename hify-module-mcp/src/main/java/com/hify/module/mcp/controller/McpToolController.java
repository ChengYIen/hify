package com.hify.module.mcp.controller;

import com.hify.common.web.Result;
import com.hify.module.mcp.controller.dto.McpToolResponse;
import com.hify.module.mcp.service.McpToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP 工具控制器.
 */
@RestController
@RequestMapping("/api/v1/mcp/tools")
@RequiredArgsConstructor
public class McpToolController {

    private final McpToolService mcpToolService;

    @GetMapping
    public Result<List<McpToolResponse>> list() {
        return Result.ok(mcpToolService.listAllEnabled());
    }
}
