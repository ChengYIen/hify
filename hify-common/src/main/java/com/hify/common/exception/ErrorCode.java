package com.hify.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举.
 * <p>
 * 分段管理：
 * <pre>
 *   0       — 成功
 *   1xxxx   — 通用错误（参数、权限、系统）
 *   2xxxx   — provider（模型提供商）
 *   3xxxx   — agent
 *   4xxxx   — conversation（对话）
 *   5xxxx   — knowledge（知识库）
 *   6xxxx   — workflow（工作流）
 *   7xxxx   — mcp / LLM 调用
 * </pre>
 * 每个模块预留 1000 个子码（如 20001–20999）。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ======================== 通用 ========================
    SUCCESS(0, "success"),

    // --- 参数 / 校验 10001–10099 ---
    PARAM_INVALID(10001, "参数错误"),
    PARAM_MISSING(10002, "缺少必填参数"),
    PARAM_TYPE_MISMATCH(10003, "参数类型不匹配"),
    PARAM_RANGE(10004, "参数超出允许范围"),

    // --- 认证 / 授权 10100–10199 ---
    UNAUTHORIZED(10100, "未登录或 token 已过期"),
    FORBIDDEN(10101, "无权限访问"),
    TOKEN_EXPIRED(10102, "token 已过期，请重新登录"),
    TOKEN_INVALID(10103, "token 无效"),

    // --- 数据 10200–10299 ---
    NOT_FOUND(10200, "数据不存在"),
    DUPLICATE(10201, "数据已存在"),
    DATA_CONFLICT(10202, "数据冲突，请刷新后重试"),

    // --- 系统 10300–10399 ---
    SYSTEM_ERROR(10300, "系统内部错误"),
    SERVICE_UNAVAILABLE(10301, "服务暂不可用，请稍后重试"),
    RATE_LIMITED(10302, "请求过于频繁，请稍后重试"),
    DB_ERROR(10303, "数据库异常"),
    IO_ERROR(10304, "IO 异常"),

    // ======================== Provider 20001–20999 ========================
    PROVIDER_NOT_FOUND(20001, "模型提供商不存在"),
    PROVIDER_DISABLED(20002, "模型提供商已禁用"),
    PROVIDER_API_KEY_INVALID(20003, "API Key 无效"),
    PROVIDER_CONFIG_ERROR(20004, "提供商配置错误"),
    PROVIDER_NAME_DUPLICATE(20005, "提供商名称已存在"),

    // ======================== Agent 30001–30999 ========================
    AGENT_NOT_FOUND(30001, "Agent 不存在"),
    AGENT_DISABLED(30002, "Agent 已禁用"),
    AGENT_CONFIG_INVALID(30003, "Agent 配置无效"),

    // ======================== Conversation 40001–40999 ========================
    CONVERSATION_NOT_FOUND(40001, "对话不存在"),
    CONVERSATION_TIMEOUT(40002, "对话超时"),
    SESSION_EXPIRED(40003, "对话会话已过期"),

    // ======================== Knowledge 50001–50999 ========================
    KNOWLEDGE_NOT_FOUND(50001, "知识库不存在"),
    KNOWLEDGE_DOC_PARSE_FAILED(50002, "文档解析失败"),
    KNOWLEDGE_RETRIEVE_FAILED(50003, "知识检索失败"),
    KNOWLEDGE_CONFIG_INVALID(50005, "知识库配置无效"),

    // ======================== Workflow 60001–60999 ========================
    WORKFLOW_NOT_FOUND(60001, "工作流不存在"),
    WORKFLOW_EXECUTION_FAILED(60002, "工作流执行失败"),
    WORKFLOW_NODE_TIMEOUT(60003, "工作流节点超时"),

    // ======================== MCP / LLM 70001–70999 ========================
    LLM_CALL_FAILED(70001, "LLM 调用失败"),
    LLM_TIMEOUT(70002, "LLM 调用超时"),
    LLM_QUOTA_EXCEEDED(70003, "LLM 配额超限"),
    LLM_ALL_MODELS_FAILED(70004, "所有模型均调用失败"),
    MCP_TOOL_NOT_FOUND(70005, "MCP 工具不存在"),
    MCP_TOOL_EXECUTION_FAILED(70006, "MCP 工具执行失败"),
    MCP_SERVER_UNAVAILABLE(70007, "MCP 服务器不可用"),
    MCP_TRANSPORT_NOT_SUPPORTED(70008, "不支持的传输协议"),
    MCP_SERVER_NOT_FOUND(70009, "MCP 服务器不存在"),
    MCP_TOOL_CALL_FAILED(70010, "MCP 工具调用失败"),

    // ======================== Auth 10104—10199 ========================
    AUTH_USERNAME_EXISTS(10104, "用户名已存在"),
    AUTH_CREDENTIALS_INVALID(10105, "用户名或密码错误"),

    // ======================== Agent 补充 ========================
    AGENT_TOOL_NOT_FOUND(30004, "Agent 工具不存在"),

    // ======================== Conversation 补充 ========================
    SESSION_AGENT_MISMATCH(40004, "会话与 Agent 不匹配"),

    // ======================== Knowledge 补充 ========================
    DOCUMENT_NOT_FOUND(50004, "文档不存在"),

    // ======================== Workflow 补充 ========================
    WORKFLOW_INVALID_DEFINITION(60004, "工作流定义格式无效"),
    WORKFLOW_ALREADY_RUNNING(60005, "工作流正在执行中"),
    ;

    /** 错误码 */
    private final int code;

    /** 默认错误信息 */
    private final String message;
}
