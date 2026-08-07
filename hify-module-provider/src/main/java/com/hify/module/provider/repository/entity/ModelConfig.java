package com.hify.module.provider.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型配置实体.
 *
 * <p>对应表 {@code hify_provider_model}。每个 provider 实例下可配置多个模型，
 * 模型由 {@link #modelName} 唯一标识（API 调用时传给 LLM 的 model 参数）。</p>
 *
 * <h3>能力标记</h3>
 * 常用的能力（视觉、工具调用、流式）用独立 TINYINT 列——方便 SQL 查询过滤；
 * 低频/新增能力放在 {@link #extraParams} JSON 中，使用 {@link JacksonTypeHandler} 序列化。
 *
 * <h3>降级链路</h3>
 * {@link #fallbackModelId} 指向同 provider 下的另一个模型 ID，
 * 当主模型不可用（限流/超时/熔断）时自动切换。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "hify_provider_model", autoResultMap = true)
public class ModelConfig extends BaseEntity {

    // -------------------------------------------------------
    // 归属
    // -------------------------------------------------------

    /** 所属提供商实例 ID（关联 hify_provider.id） */
    private Long providerId;

    // -------------------------------------------------------
    // 标识
    // -------------------------------------------------------

    /** API 模型标识符（如 gpt-4o / claude-sonnet-4-20250514 / gemini-2.5-pro） */
    private String modelName;

    /** 前端展示名（如 GPT-4o / Claude Sonnet 4），为 null 则用 modelName */
    private String displayName;

    /** 模型类型：LLM / EMBEDDING / IMAGE / TTS / RERANK */
    private String modelType;

    // -------------------------------------------------------
    // 容量参数
    // -------------------------------------------------------

    /** 上下文窗口 Token 数 */
    private Integer contextWindow;

    /** 最大输出 Token 数 */
    private Integer maxOutput;

    // -------------------------------------------------------
    // 能力标记（高频查询 → TINYINT 独立列）
    // -------------------------------------------------------

    /** 是否支持图片输入 */
    private Integer supportsVision;

    /** 是否支持工具调用（function calling） */
    private Integer supportsTools;

    /** 是否支持流式输出（SSE） */
    private Integer supportsStreaming;

    /**
     * 扩展能力参数（JSON，对应 capabilities 列）.
     * <p>低频/新增能力放这里，使用 JacksonTypeHandler 自动序列化。</p>
     */
    @TableField(value = "capabilities", typeHandler = JacksonTypeHandler.class)
    private ModelExtraParams extraParams;

    // -------------------------------------------------------
    // 降级 & 优先级
    // -------------------------------------------------------

    /** 优先级，数值越大越优先（同一 provider 内选默认模型时用） */
    private Integer priority;

    /** 降级备选模型 ID（同 provider 内，主模型不可用时自动切换） */
    private Long fallbackModelId;

    // -------------------------------------------------------
    // 状态
    // -------------------------------------------------------

    /** 状态：ENABLED / DISABLED */
    private String status;
}
