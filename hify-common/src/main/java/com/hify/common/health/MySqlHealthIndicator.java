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
 * MySQL 健康检查 —— 执行 {@code SELECT 1} 验证数据库连通性.
 * <p>
 * 暴露在 {@code /actuator/health/readiness} 中，
 * K8s readiness probe 检测到此项 DOWN 时会停止将流量路由到该 Pod。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class MySqlHealthIndicator implements HealthIndicator {

    @Qualifier("mysqlDataSource")
    private final DataSource mysqlDataSource;

    @Override
    public Health health() {
        try (Connection conn = mysqlDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return Health.up()
                    .withDetail("database", "MySQL")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "MySQL")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
