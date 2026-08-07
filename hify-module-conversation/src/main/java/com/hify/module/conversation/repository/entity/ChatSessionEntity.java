package com.hify.module.conversation.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话会话实体.
 * <p>
 * 对应表 {@code hify_chat_session}。每次用户发起对话时创建一个 Session，
 * Session 下包含多条消息（ChatMessageEntity）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_chat_session")
public class ChatSessionEntity extends BaseEntity {

    /** 会话标题（默认取首条用户消息摘要） */
    private String title;

    /** 所属用户 ID */
    private Long userId;

    /** 使用的 Agent ID（为空则为自由对话） */
    private Long agentId;

    /** 使用的模型 ID */
    private Long modelId;

    /** 状态：ACTIVE / ARCHIVED / ERROR */
    private String status;

    /** 消息总数（冗余，避免 COUNT） */
    private Integer messageCount;

    /** 累计 Token 消耗 */
    private Integer totalTokens;
}
