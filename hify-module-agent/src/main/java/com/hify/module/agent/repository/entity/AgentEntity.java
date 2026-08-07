package com.hify.module.agent.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Agent 配置实体.
 * <p>
 * 对应表 {@code hify_agent}。定义 Agent 的行为：系统提示词、默认模型、
 * 温度、最大迭代次数、关联工具和知识库等。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_agent")
public class AgentEntity extends BaseEntity {

    /** Agent 名称 */
    private String name;

    /** Agent 描述 */
    private String description;

    /** 头像 URL */
    private String avatarUrl;

    /** 系统提示词 */
    private String systemPrompt;

    /** 默认模型 ID（关联 hify_provider_model.id） */
    private Long modelId;

    /** 温度 0.00–2.00 */
    private BigDecimal temperature;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** Agent 循环最大迭代次数 */
    private Integer maxIterations;

    /** 是否启用工具调用 */
    private Integer toolsEnabled;

    /** 关联知识库 ID 列表（JSON 数组） */
    private String knowledgeIds;

    /** 状态：ENABLED / DISABLED / DRAFT */
    private String status;

    /** 创建人用户 ID */
    private Long createdBy;
}
