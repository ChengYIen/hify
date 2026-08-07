package com.hify.shared.conversation;

/**
 * 会话查询接口（跨模块共享）.
 * <p>
 * Agent 模块通过此接口查询 Agent 是否有活跃对话引用，
 * 避免跨模块直接 import conversation 模块的 Mapper。
 * </p>
 */
public interface SessionQueryApi {

    /**
     * 统计指定 Agent 的活跃会话数.
     *
     * @param agentId Agent ID
     * @return 活跃会话数（status=ACTIVE 且未删除）
     */
    long countActiveByAgentId(Long agentId);
}
