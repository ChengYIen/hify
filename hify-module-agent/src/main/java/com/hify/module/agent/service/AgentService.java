package com.hify.module.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.agent.controller.dto.AgentCreateRequest;
import com.hify.module.agent.controller.dto.AgentDetailResponse;
import com.hify.module.agent.controller.dto.AgentListResponse;
import com.hify.module.agent.controller.dto.AgentResponse;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.controller.dto.AgentUpdateRequest;

import java.util.List;

/**
 * Agent 配置业务接口.
 */
public interface AgentService {

    IPage<AgentListResponse> page(int page, int pageSize);

    AgentResponse getById(Long id);

    AgentResponse getByIdWithTools(Long id);

    AgentDetailResponse create(AgentCreateRequest request);

    AgentResponse update(Long id, AgentUpdateRequest request);

    void delete(Long id);

    /**
     * 全量替换 Agent 的工具绑定列表.
     */
    List<AgentToolResponse> updateTools(Long id, List<Long> toolIds);
}
