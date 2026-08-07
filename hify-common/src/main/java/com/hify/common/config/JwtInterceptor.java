package com.hify.common.config;

import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.BizException;
import com.hify.common.util.JwtUtil;
import com.hify.common.web.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器.
 * <p>
 * 从 {@code Authorization: Bearer <token>} 解析 JWT，
 * 写入 {@link UserContext}，供后续链路使用。
 * </p>
 * <p>
 * 公开路径（如 {@code /api/v1/public/**}）由
 * {@link WebMvcConfig} 排除，不走此拦截器。
 * </p>
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String token = extractToken(request);
        if (token == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少认证令牌");
        }

        Claims claims = JwtUtil.parse(token);
        if (claims == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = JwtUtil.parseUserId(token);
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);

        UserContext.setUserId(userId);
        UserContext.setUsername(username);
        UserContext.setRole(role);

        log.debug("认证通过 userId={}, username={}, role={}", userId, username, role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // 防止内存泄漏
        UserContext.clear();
    }

    /**
     * 从请求头提取 Token.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
