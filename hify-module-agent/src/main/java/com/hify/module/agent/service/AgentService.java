package com.hify.module.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.agent.controller.dto.AgentCreateRequest;
import com.hify.module.agent.controller.dto.AgentDetailResponse;
import com.hify.module.agent.controller.dto.AgentListResponse;
import com.hify.module.agent.controller.dto.AgentResponse;
import com.hify.module.agent.controller.dto.AgentToolRequest;
import com.hify.module.agent.controller.dto.AgentUpdateRequest;

import java.util.List;

/**
 * Agent 配置业务接口.
 */
public interface AgentService {

    IPage<AgentListResponse> page(int page, int pageSize);

    AgentResponse getById(Long id);

    AgentResponse getByIdWithTools(Long id);

    /** 创建 Agent（返回详情含工具列表） */
    AgentDetailResponse create(AgentCreateRequest request);

    AgentResponse update(Long id, AgentUpdateRequest request);

    void delete(Long id);

    /** 独立工具更新接口：全量替换 Agent 的工具列表 */
    List<AgentToolRequest> updateTools(Long id, List<AgentToolRequest> tools);
}
