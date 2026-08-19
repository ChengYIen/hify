package com.hify.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hify.common.health.MySqlHealthIndicator;
import com.hify.common.health.PgVectorHealthIndicator;
import com.hify.common.health.RedisHealthIndicator;
import com.hify.common.metrics.HifyMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口.
 * <p>
 * 聚合 MySQL、Redis、pgvector 三个依赖的真实连通性，返回各依赖状态与错误信息；
 * 全部 UP 时整体 UP（HTTP 200），任一 DOWN 则整体 DOWN（HTTP 503），
 * 供 K8s 健康探针（startup/liveness/readiness）使用。
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    private final MySqlHealthIndicator mysqlHealthIndicator;
    private final RedisHealthIndicator redisHealthIndicator;
    private final PgVectorHealthIndicator pgVectorHealthIndicator;
    private final HifyMetrics hifyMetrics;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, ComponentStatus> components = new LinkedHashMap<>();
        components.put("mysql", toComponent(mysqlHealthIndicator.health()));
        components.put("redis", toComponent(redisHealthIndicator.health()));
        components.put("pgvector", toComponent(pgVectorHealthIndicator.health()));
        components.forEach((name, c) ->
                hifyMetrics.componentHealth(name, "UP".equals(c.status())));

        boolean allUp = components.values().stream()
                .allMatch(c -> "UP".equals(c.status()));
        String status = allUp ? "UP" : "DOWN";
        HttpStatus httpStatus = allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(new HealthResponse(status, components));
    }

    private ComponentStatus toComponent(Health health) {
        Object error = health.getDetails().get("error");
        return new ComponentStatus(
                health.getStatus().getCode(),
                error == null ? null : String.valueOf(error));
    }

    /** 健康检查响应体. */
    public record HealthResponse(String status, Map<String, ComponentStatus> components) {
    }

    /** 单个依赖状态：status 为 UP/DOWN，error 仅在异常时出现. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ComponentStatus(String status, String error) {
    }
}
