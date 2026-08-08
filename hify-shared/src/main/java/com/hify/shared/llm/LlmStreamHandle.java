package com.hify.shared.llm;

/**
 * 流式 LLM 调用句柄 —— 用于主动取消进行中的流.
 *
 * <p>由 {@link LlmProviderApi#streamChat} 返回。当客户端断开、超时等场景需要
 * 立即停止 LLM 调用（不再消耗 token / 带宽）时，调用 {@link #cancel()}。
 * 实现方（provider 模块）底层对应 OkHttp {@code Call#cancel()}，多次调用幂等。</p>
 */
public interface LlmStreamHandle {

    /**
     * 取消进行中的流式调用. 幂等，重复调用无副作用。
     * 取消后底层连接关闭，已回调的增量不受影响，未消费的流将被中断。
     */
    void cancel();
}
