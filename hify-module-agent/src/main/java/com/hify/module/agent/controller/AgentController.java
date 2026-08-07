package com.hify.module.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.agent.controller.dto.AgentCreateRequest;
import com.hify.module.agent.controller.dto.AgentDetailResponse;
import com.hify.module.agent.controller.dto.AgentListResponse;
import com.hify.module.agent.controller.dto.AgentResponse;
import com.hify.module.agent.controller.dto.AgentToolRequest;
import com.hify.module.agent.controller.dto.AgentUpdateRequest;
import com.hify.module.agent.service.AgentService;
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

import java.util.List;

/**
 * Agent 控制器.
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public PageResult<AgentListResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        IPage<AgentListResponse> result = agentService.page(
                page != null ? page : 1,
                pageSize != null ? pageSize : 20);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<AgentResponse> get(@PathVariable Long id) {
        return Result.ok(agentService.getByIdWithTools(id));
    }

    @PostMapping
    public Result<AgentDetailResponse> create(@Valid @RequestBody AgentCreateRequest request) {
        return Result.ok(agentService.create(request));
    }

    @PutMapping("/{id}")
    public Result<AgentResponse> update(@PathVariable Long id,
                                         @Valid @RequestBody AgentUpdateRequest request) {
        return Result.ok(agentService.update(id, request));
    }

    @PutMapping("/{id}/tools")
    public Result<List<AgentToolRequest>> updateTools(
            @PathVariable Long id,
            @Valid @RequestBody List<AgentToolRequest> tools) {
        return Result.ok(agentService.updateTools(id, tools));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }
}
