package com.hify.common.web;

/**
 * 请求上下文 —— 基于 ThreadLocal，请求链路内随时获取当前用户信息.
 * <p>
 * 由 {@link com.hify.common.config.JwtInterceptor} 在请求入口写入，
 * {@code afterCompletion} 时自动清除，防止内存泄漏。
 * </p>
 * <p>
 * 使用方式：{@code UserContext.getUserId()}
 * </p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private UserContext() {
        // 工具类，禁止实例化
    }

    // ----------------------------------------------------
    // 写入（仅供 JwtInterceptor 调用）
    // ----------------------------------------------------

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static void setRole(String role) {
        ROLE.set(role);
    }

    // ----------------------------------------------------
    // 读取（业务代码使用）
    // ----------------------------------------------------

    /** 获取当前用户 ID，未登录返回 null. */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 获取当前用户名，未登录返回 null. */
    public static String getUsername() {
        return USERNAME.get();
    }

    /** 获取当前用户角色，未登录返回 null. */
    public static String getRole() {
        return ROLE.get();
    }

    // ----------------------------------------------------
    // 清理
    // ----------------------------------------------------

    /** 移除全部 ThreadLocal，必须由拦截器在 afterCompletion 调用. */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLE.remove();
    }
}
