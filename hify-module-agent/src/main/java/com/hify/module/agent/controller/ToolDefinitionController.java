package com.hify.module.agent.controller;

import com.hify.common.web.Result;
import com.hify.module.agent.controller.dto.ToolDefinitionResponse;
import com.hify.module.agent.service.ToolDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具定义控制器.
 */
@RestController
@RequestMapping("/api/v1/tool-definitions")
@RequiredArgsConstructor
public class ToolDefinitionController {

    private final ToolDefinitionService toolDefinitionService;

    @GetMapping
    public Result<List<ToolDefinitionResponse>> listEnabled() {
        return Result.ok(toolDefinitionService.listEnabled());
    }
}
