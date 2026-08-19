package com.hify.common.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.metrics.jdbc.DataSourcePoolMetrics;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.jdbc.metadata.HikariDataSourcePoolMetadata;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Collections;

/**
 * 双数据源配置 —— MySQL（业务主库） + PostgreSQL（向量检索）.
 * <p>
 * Spring Boot 的 {@code DataSourceAutoConfiguration} 已排除，两个数据源手动装配。
 * </p>
 * <p>
 * MyBatis-Plus 的 SqlSessionFactory / SqlSessionTemplate 由
 * {@code MybatisPlusAutoConfiguration} 自动创建，无需手动配置。
 * 它会自动选取 {@code @Primary} 的 mysqlDataSource。
 * </p>
 *
 * <h3>数据源分工</h3>
 * <table>
 *   <tr><th>库</th><th>技术</th><th>用途</th></tr>
 *   <tr><td>MySQL（主）</td><td>MyBatis-Plus</td><td>业务 CRUD</td></tr>
 *   <tr><td>PostgreSQL</td><td>JdbcTemplate + pgvector</td><td>向量检索</td></tr>
 * </table>
 */
@Configuration
public class DataSourceConfig {

    // ================================================================
    // 数据源 Bean
    // ================================================================

    /**
     * MySQL 数据源 —— 主数据源.
     * <p>
     * 连接池 HikariCP，配置键 {@code spring.datasource.mysql.*}。
     * 设为 {@link Primary}，MyBatis-Plus 自动选取此数据源。
     * </p>
     */
    @Primary
    @Bean(name = "mysqlDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * PostgreSQL 数据源 —— pgvector 向量检索专用.
     * <p>
     * 连接池 HikariCP，配置键 {@code spring.datasource.postgresql.*}。
     * 不走 MyBatis-Plus，只用 JdbcTemplate + pgvector 库。
     * </p>
     */
    @Bean(name = "postgresqlDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.postgresql")
    public DataSource postgresqlDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * 为两个 Hikari 数据源注册 {@code jdbc.connections.*} 指标。
     * <p>
     * 应用排除了 {@code DataSourceAutoConfiguration} 且数据源为手动装配，
     * actuator 的 DataSourcePoolMetrics 自动绑定在此场景不会生效，因此显式绑定。
     * </p>
     */
    @Bean
    public DataSourcePoolMetrics mysqlDataSourcePoolMetrics(
            MeterRegistry meterRegistry,
            @Qualifier("mysqlDataSource") DataSource dataSource) {
        DataSourcePoolMetrics metrics = new DataSourcePoolMetrics(
                dataSource, hikariPoolMetadataProvider(), "mysqlDataSource", Collections.emptyList());
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public DataSourcePoolMetrics postgresqlDataSourcePoolMetrics(
            MeterRegistry meterRegistry,
            @Qualifier("postgresqlDataSource") DataSource dataSource) {
        DataSourcePoolMetrics metrics = new DataSourcePoolMetrics(
                dataSource, hikariPoolMetadataProvider(), "postgresqlDataSource", Collections.emptyList());
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    private DataSourcePoolMetadataProvider hikariPoolMetadataProvider() {
        return dataSource -> dataSource instanceof HikariDataSource
                ? new HikariDataSourcePoolMetadata((HikariDataSource) dataSource)
                : null;
    }

    // ================================================================
    // 事务管理器
    // ================================================================

    /**
     * MySQL 事务管理器 —— {@code @Transactional} 默认使用.
     */
    @Primary
    @Bean(name = "mysqlTransactionManager")
    public PlatformTransactionManager mysqlTransactionManager(
            @Qualifier("mysqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * PostgreSQL 事务管理器.
     * <p>
     * 使用时需显式指定：
     * {@code @Transactional(transactionManager = "postgresqlTransactionManager")}.
     * </p>
     */
    @Bean(name = "postgresqlTransactionManager")
    public PlatformTransactionManager postgresqlTransactionManager(
            @Qualifier("postgresqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    // ================================================================
    // PostgreSQL — JdbcTemplate
    // ================================================================

    /**
     * PostgreSQL 专用 JdbcTemplate，供 knowledge 模块执行向量查询.
     */
    @Bean(name = "postgresqlJdbcTemplate")
    public JdbcTemplate postgresqlJdbcTemplate(
            @Qualifier("postgresqlDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
