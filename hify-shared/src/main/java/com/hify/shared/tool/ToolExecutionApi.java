package com.hify.shared.tool;

import com.hify.shared.tool.dto.ToolResultDTO;

import java.util.Map;

/**
 * 工具执行接口（跨模块共享）.
 * <p>
 * agent 循环通过此接口执行工具调用，
 * 由 mcp 模块实现。
 * </p>
 */
public interface ToolExecutionApi {

    /**
     * 执行工具调用.
     *
     * @param toolName 工具名称
     * @param params   工具参数
     * @return 工具执行结果
     */
    ToolResultDTO execute(String toolName, Map<String, Object> params);
}
