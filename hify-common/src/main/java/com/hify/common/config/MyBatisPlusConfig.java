package com.hify.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置.
 * <p>
 * 包含分页插件；逻辑删除、驼峰映射、mapper 路径等由 application.yml 管理。
 */
@Configuration
@MapperScan("com.hify.module.**.repository")
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链.
     * <p>
     * 目前仅注册分页插件，后续可按需追加防全表更新、乐观锁等。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件，指定数据库类型为 MySQL
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大 500 条，防止误写导致全表扫描
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
