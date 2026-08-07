package com.hify.module.conversation.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话消息实体.
 * <p>
 * 对应表 {@code hify_chat_message}。记录每条对话消息的完整信息，
 * 包括角色、内容、Token 用量、工具调用等。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_chat_message")
public class ChatMessageEntity extends BaseEntity {

    /** 所属会话 ID（关联 hify_chat_session.id） */
    private Long sessionId;

    /** 角色：system / user / assistant / tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** 实际使用的模型名称（仅 assistant 消息） */
    private String model;

    /** Token 用量（JSON：promptTokens / completionTokens / totalTokens） */
    private String tokenUsage;

    /** 停止原因：stop / length / tool_calls / content_filter */
    private String finishReason;

    /** 工具调用请求（JSON，模型要求调用工具时） */
    private String toolCalls;

    /** 工具调用 ID（role=tool 时回填） */
    private String toolCallId;

    /** 是否降级模型响应 */
    private Integer fallback;

    /** 消息序号（会话内递增） */
    private Integer seq;
}
