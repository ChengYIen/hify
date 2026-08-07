package com.hify.module.agent.controller;

import com.hify.common.web.Result;
import com.hify.module.agent.controller.dto.AgentToolRequest;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.service.AgentToolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 工具控制器.
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

    @PostMapping
    public Result<AgentToolResponse> create(@PathVariable Long agentId,
                                             @Valid @RequestBody AgentToolRequest request) {
        return Result.ok(agentToolService.create(agentId, request));
    }

    @PutMapping("/{id}")
    public Result<AgentToolResponse> update(@PathVariable Long agentId,
                                             @PathVariable Long id,
                                             @Valid @RequestBody AgentToolRequest request) {
        return Result.ok(agentToolService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long agentId, @PathVariable Long id) {
        agentToolService.delete(id);
        return Result.ok();
    }
}
