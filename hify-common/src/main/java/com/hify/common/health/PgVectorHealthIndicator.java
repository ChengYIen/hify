package com.hify.common.health;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * pgvector 健康检查 —— 对 PostgreSQL（向量库）数据源执行 {@code SELECT 1} 验证连通性.
 * <p>
 * 与 {@link MySqlHealthIndicator} 同理，供 /api/v1/health 聚合展示及 K8s 健康探针使用。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class PgVectorHealthIndicator implements HealthIndicator {

    @Qualifier("postgresqlDataSource")
    private final DataSource postgresqlDataSource;

    @Override
    public Health health() {
        try (Connection conn = postgresqlDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return Health.up()
                    .withDetail("database", "pgvector")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "pgvector")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
