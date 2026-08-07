package com.hify.module.conversation.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送消息请求.
 */
@Data
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    /**
     * true = SSE 流式返回（{@code delta}/{@code done}/{@code error} 类型化事件）；
     * false / 缺省 = 同步阻塞返回完整 {@code ChatMessageResponse}.
     */
    private Boolean stream;
}
