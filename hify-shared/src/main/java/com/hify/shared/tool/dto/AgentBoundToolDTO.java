package com.hify.shared.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent bound MCP tool (cross-module shared DTO).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentBoundToolDTO {

    /** Tool name exposed to the LLM. */
    private String toolName;

    /** Tool description. */
    private String description;

    /** Tool input JSON Schema, stored as JSON string. */
    private String inputSchema;

    /** Owning MCP Server id, used to execute the tool. */
    private Long mcpServerId;
}
