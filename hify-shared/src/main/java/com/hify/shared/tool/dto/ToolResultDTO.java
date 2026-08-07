package com.hify.shared.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果 DTO（跨模块共享）.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResultDTO {

    /** 是否执行成功 */
    private Boolean success;

    /** 执行结果内容 */
    private String content;

    /** 错误信息（失败时） */
    private String errorMessage;
}
