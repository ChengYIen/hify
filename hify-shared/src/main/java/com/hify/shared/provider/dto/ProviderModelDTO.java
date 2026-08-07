package com.hify.shared.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置 DTO（跨模块共享）.
 * <p>
 * 用于 Agent 配置、对话引擎等模块按需查询可用模型信息。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderModelDTO {

    /** 模型 ID */
    private Long id;

    /** 所属提供商实例 ID */
    private Long providerId;

    /** API 模型标识符 */
    private String modelName;

    /** 前端展示名 */
    private String displayName;

    /** 模型类型 */
    private String modelType;

    /** 上下文窗口 Token 数 */
    private Integer contextWindow;

    /** 最大输出 Token 数 */
    private Integer maxOutput;

    /** 是否支持图片输入 */
    private Boolean supportsVision;

    /** 是否支持工具调用 */
    private Boolean supportsTools;

    /** 是否支持流式输出 */
    private Boolean supportsStreaming;

    /** 优先级 */
    private Integer priority;

    /** 降级备选模型 ID */
    private Long fallbackModelId;

    /** 状态 */
    private String status;
}
