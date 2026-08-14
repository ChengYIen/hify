package com.hify.module.agent.controller;

import com.hify.common.web.Result;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.service.AgentToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 工具关联控制器.
 */
@RestController
@RequestMapping("/api/v1/agents/{agentId}/tools")
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolService agentToolService;

    @GetMapping
    public Result<List<AgentToolResponse>> list(@PathVariable Long agentId) {
        return Result.ok(agentToolService.listByAgentId(agentId));
    }

    @GetMapping("/{id}")
    public Result<AgentToolResponse> get(@PathVariable Long agentId, @PathVariable Long id) {
        return Result.ok(agentToolService.getById(id));
    }
}
