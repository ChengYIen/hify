package com.hify.module.mcp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.mcp.controller.dto.ConnectionTestResult;
import com.hify.module.mcp.controller.dto.McpServerCreateRequest;
import com.hify.module.mcp.controller.dto.McpServerResponse;
import com.hify.module.mcp.controller.dto.McpServerUpdateRequest;
import com.hify.module.mcp.service.McpConnectivityService;
import com.hify.module.mcp.service.McpServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Server 控制器.
 */
@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpConnectivityService mcpConnectivityService;

    @GetMapping
    public PageResult<McpServerResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        IPage<McpServerResponse> result = mcpServerService.page(
                page != null ? page : 1,
                pageSize != null ? pageSize : 20);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<McpServerResponse> get(@PathVariable Long id) {
        return Result.ok(mcpServerService.getById(id));
    }

    @PostMapping
    public Result<McpServerResponse> create(@Valid @RequestBody McpServerCreateRequest request) {
        return Result.ok(mcpServerService.create(request));
    }

    @PutMapping("/{id}")
    public Result<McpServerResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody McpServerUpdateRequest request) {
        return Result.ok(mcpServerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<ConnectionTestResult> test(@PathVariable Long id) {
        return Result.ok(mcpConnectivityService.testConnection(id));
    }
}
