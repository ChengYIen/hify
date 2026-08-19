package com.hify.common.config;

import com.hify.common.log.RequestLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 —— 注册拦截器.
 * <p>
 * 拦截器执行顺序（先注册 → preHandle 先执行，afterCompletion 后执行）：
 * <ol>
 *   <li>{@link RequestLogInterceptor} — traceId 初始化 + 请求日志</li>
 *   <li>{@link JwtInterceptor} — JWT 鉴权（仅 /api/**）</li>
 * </ol>
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestLogInterceptor requestLogInterceptor;
    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 请求日志 — 拦截所有路径（含公开路径），必须最先注册
        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/v1/health",
                        "/actuator/health/**" // 健康检查不打日志，避免噪音
                );

        // 2. JWT 鉴权 — 仅拦截 /api/**
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                // 公开路径放行
                .excludePathPatterns(
                        "/api/v1/public/**",
                        "/api/v1/auth/**",
                        "/api/v1/health",
                        "/actuator/**"
                );
    }
}
