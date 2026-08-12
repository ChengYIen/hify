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

    /**
     * 会话 ID，可选。为空时由服务端自动创建新会话
     * （标题取首条消息摘要，模型取第一个可用模型）。
     */
    private Long sessionId;

    /**
     * Agent ID，可选。仅 {@code sessionId} 为空、自动创建新会话时生效：
     * 新会话绑定该 Agent，模型优先取 Agent 绑定模型。
     */
    private Long agentId;
}
