package com.hify.module.mcp.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 服务配置实体.
 * <p>
 * 对应表 {@code hify_mcp_server}。支持 stdio、SSE、Streamable 三种传输协议。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_mcp_server")
public class McpServerEntity extends BaseEntity {

    /** MCP 服务名称 */
    private String name;

    /** 服务描述 */
    private String description;

    /** 传输协议：stdio / sse / streamable */
    private String transport;

    /** 启动命令（stdio 模式） */
    private String command;

    /** 命令参数（JSON 数组） */
    private String args;

    /** 环境变量（JSON 对象） */
    private String envVars;

    /** 服务 URL（sse / streamable 模式） */
    private String url;

    /** 请求头（JSON 对象） */
    private String headers;

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
