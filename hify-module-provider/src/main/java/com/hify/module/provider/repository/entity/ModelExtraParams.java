package com.hify.module.provider.repository.entity;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型扩展参数 POJO，对应 {@code capabilities} JSON 列.
 *
 * <p>配合 MyBatis-Plus JacksonTypeHandler 序列化/反序列化。
 * 高频查询能力（视觉/工具/流式）用独立 TINYINT 列，低频/新增能力放这里。</p>
 *
 * <pre>{@code
 * {
 *   "supportsStructuredOutput": true,
 *   "supportsPromptCaching": true,
 *   "supportsComputerUse": false,
 *   "audio": { "input": true, "output": false }
 * }
 * }</pre>
 */
@Data
public class ModelExtraParams {

    /** 是否支持结构化输出（JSON mode / structured output） */
    private Boolean supportsStructuredOutput;

    /** 是否支持 prompt caching（降低重复提示的 Token 成本） */
    private Boolean supportsPromptCaching;

    /** 是否支持 computer use（Claude 特有） */
    private Boolean supportsComputerUse;

    /** 音频能力 */
    private AudioCapability audio;

    /**
     * 扩展字段，存放上述标准字段以外的能力标记.
     */
    private Map<String, Object> extra = new LinkedHashMap<>();

    // -------------------------------------------------------
    // 嵌套类型
    // -------------------------------------------------------

    @Data
    public static class AudioCapability {
        private Boolean input;
        private Boolean output;
    }
}
