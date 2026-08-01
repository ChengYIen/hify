package com.hify.common.exception;

import lombok.Getter;

/**
 * 业务异常 —— 持有一个 {@link ErrorCode}，可选择性覆盖 message.
 * <p>
 * 使用方式：
 * <pre>{@code
 * // 用默认 message
 * throw new BizException(ErrorCode.AGENT_NOT_FOUND);
 *
 * // 覆盖 message
 * throw new BizException(ErrorCode.PARAM_INVALID, "agentId 不能为空");
 * }</pre>
 * <p>
 * 禁止在业务代码中直接 {@code throw new RuntimeException("xxx")}，
 * 一律使用本类。
 */
@Getter
public class BizException extends RuntimeException {

    /** 错误码 */
    private final ErrorCode errorCode;

    /**
     * 使用 ErrorCode 默认 message.
     *
     * @param errorCode 错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义 message 覆盖 ErrorCode 默认值.
     *
     * @param errorCode 错误码
     * @param message   自定义错误信息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义 message 并保留原始异常.
     *
     * @param errorCode 错误码
     * @param message   自定义错误信息
     * @param cause     原始异常
     */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 使用默认 message 并保留原始异常.
     *
     * @param errorCode 错误码
     * @param cause     原始异常
     */
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
