package com.hify.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.health.MySqlHealthIndicator;
import com.hify.common.health.PgVectorHealthIndicator;
import com.hify.common.health.RedisHealthIndicator;
import com.hify.common.metrics.HifyMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final MySqlHealthIndicator mysql = mock(MySqlHealthIndicator.class);
    private final RedisHealthIndicator redis = mock(RedisHealthIndicator.class);
    private final PgVectorHealthIndicator pg = mock(PgVectorHealthIndicator.class);
    private final HifyMetrics metrics = mock(HifyMetrics.class);
    private final HealthController controller = new HealthController(mysql, redis, pg, metrics);

    @Test
    void should_reportUp_whenAllDependenciesUp() {
        when(mysql.health()).thenReturn(Health.up().build());
        when(redis.health()).thenReturn(Health.up().build());
        when(pg.health()).thenReturn(Health.up().build());

        ResponseEntity<HealthController.HealthResponse> resp = controller.health();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo("UP");
        assertThat(resp.getBody().components()).containsKeys("mysql", "redis", "pgvector");
        assertThat(resp.getBody().components()).allSatisfy((k, v) ->
                assertThat(v.status()).isEqualTo("UP"));
    }

    @Test
    void should_reportDown_whenAnyDependencyDown_andIncludeError() {
        when(mysql.health()).thenReturn(Health.up().build());
        when(redis.health())
                .thenReturn(Health.down().withDetail("error", "connection refused").build());
        when(pg.health()).thenReturn(Health.up().build());

        ResponseEntity<HealthController.HealthResponse> resp = controller.health();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().status()).isEqualTo("DOWN");
        assertThat(resp.getBody().components().get("mysql").status()).isEqualTo("UP");
        assertThat(resp.getBody().components().get("redis").status()).isEqualTo("DOWN");
        assertThat(resp.getBody().components().get("redis").error())
                .isEqualTo("connection refused");
    }

    @Test
    void should_serializeToExpectedJsonShape() throws Exception {
        when(mysql.health()).thenReturn(Health.up().build());
        when(redis.health()).thenReturn(Health.up().build());
        when(pg.health()).thenReturn(Health.up().build());

        String json = new ObjectMapper().writeValueAsString(controller.health().getBody());
        JsonNode node = new ObjectMapper().readTree(json);

        assertThat(node.path("status").asText()).isEqualTo("UP");
        assertThat(node.path("components").path("mysql").path("status").asText()).isEqualTo("UP");
        assertThat(node.path("components").path("redis").path("status").asText()).isEqualTo("UP");
        assertThat(node.path("components").path("pgvector").path("status").asText()).isEqualTo("UP");
    }
}
