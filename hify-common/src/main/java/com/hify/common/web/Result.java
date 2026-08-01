package com.hify.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体 —— 所有 Controller 返回值必须是 Result&lt;T&gt;.
 * <p>
 * code=200 表示成功，非 200 表示错误。
 * null 字段也返回，不使用 @JsonInclude。
 * </p>
 *
 * @param <T> data 类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 200 = 成功，非 200 = 错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    // ----------------------------------------------------
    // 成功
    // ----------------------------------------------------

    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ----------------------------------------------------
    // 失败
    // ----------------------------------------------------

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(int code, String message, T data) {
        return new Result<>(code, message, data);
    }
}
