package com.hify.common.web;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 分页响应体 —— 继承 {@link Result}，额外包含分页字段.
 * <p>
 * 用于需要分页数据的接口返回。
 * </p>
 *
 * @param <T> 列表元素类型
 */
@Getter
@Setter
@ToString(callSuper = true)
public class PageResult<T> extends Result<T> {

    /** 总记录数 */
    private long total;

    /** 当前页码（1-based） */
    private int page;

    /** 每页大小 */
    private int size;

    public PageResult() {
        super();
    }

    public PageResult(int code, String message, T data, long total, int page, int size) {
        super(code, message, data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    // ----------------------------------------------------
    // 静态工厂
    // ----------------------------------------------------

    /**
     * 成功分页响应.
     *
     * @param data  当前页数据列表
     * @param total 总记录数
     * @param page  当前页码
     * @param size  每页大小
     */
    public static <T> PageResult<T> ok(T data, long total, int page, int size) {
        return new PageResult<>(0, "success", data, total, page, size);
    }
}
