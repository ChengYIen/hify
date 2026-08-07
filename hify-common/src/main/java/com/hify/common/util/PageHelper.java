package com.hify.common.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.web.PageResult;

/**
 * 分页工具类.
 * <p>
 * 前端参数 → MyBatis-Plus Page 对象 → 统一 PageResult 响应体。
 * 所有分页查询走这个工具，确保分页行为一致。
 * </p>
 */
public final class PageHelper {

    private PageHelper() {
        // 工具类，禁止实例化
    }

    /** 默认页码 */
    private static final int DEFAULT_PAGE = 1;
    /** 默认每页大小 */
    private static final int DEFAULT_SIZE = 20;
    /** 最大每页大小 */
    private static final int MAX_SIZE = 100;

    /**
     * 将前端分页参数转为 MyBatis-Plus {@link Page} 对象.
     * <p>
     * page 从 1 开始（与前端一致），自动做下限/上限保护。
     * </p>
     *
     * @param page     当前页码（1-based，可传 null）
     * @param pageSize 每页大小（可传 null）
     * @param <T>      实体类型
     * @return MyBatis-Plus Page 对象
     */
    public static <T> Page<T> toPage(Integer page, Integer pageSize) {
        int current = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_SIZE : Math.min(pageSize, MAX_SIZE);
        return new Page<>(current, size);
    }

    /**
     * 将 MyBatis-Plus 分页结果转为 {@link PageResult}.
     * <p>
     * 用法：在 Service 层拿到 IPage 后调用 {@code PageHelper.toPageResult(ipage)}，
     * Controller 直接返回即可。
     * </p>
     *
     * @param page MyBatis-Plus 分页查询结果
     * @param <T>  列表元素类型
     * @return 统一分页响应体
     */
    @SuppressWarnings("unchecked")
    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        return PageResult.ok(
                (T) page.getRecords(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize()
        );
    }
}
