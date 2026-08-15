package com.hify.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@Rollback
class ProviderCrudIntegrationTest extends IntegrationTestBase {

    private static final String PROVIDER_PATH = "/api/v1/providers";

    @Test
    void should_createProvider_whenRequestValid() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post(PROVIDER_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "OpenAI-New",
                                "description", "integration test",
                                "providerCode", "openai",
                                "baseUrl", "https://api.openai.com/v1",
                                "authConfig", Map.of("apiKey", "sk-test-123"),
                                "priority", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // Then
        JsonNode root = readBody(result);
        assertThat(root.path("data").path("id").asLong()).isPositive();
    }

    @Test
    @Sql(scripts = "/sql/provider/insert-provider.sql")
    void should_returnProviderNameDuplicate_whenNameExists() throws Exception {
        // When / Then
        mockMvc.perform(post(PROVIDER_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "OpenAI-IT",
                                "providerCode", "openai"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20005));
    }

    @Test
    @Sql(scripts = "/sql/provider/insert-provider.sql")
    void should_getProvider_whenExists() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get(PROVIDER_PATH + "/9001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // Then
        JsonNode data = readBody(result).path("data");
        assertThat(data.path("id").asLong()).isEqualTo(9001L);
        assertThat(data.path("name").asText()).isEqualTo("OpenAI-IT");
        assertThat(data.path("description").asText()).isEqualTo("integration test provider");
        assertThat(data.path("providerCode").asText()).isEqualTo("openai");
        assertThat(data.path("baseUrl").asText()).isEqualTo("https://api.openai.com/v1");
        assertThat(data.path("status").asText()).isEqualTo("ENABLED");
        assertThat(data.path("healthStatus").asText()).isEqualTo("UNKNOWN");
    }

    @Test
    void should_returnProviderNotFound_whenIdNotExists() throws Exception {
        // When / Then
        mockMvc.perform(get(PROVIDER_PATH + "/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @Sql(scripts = "/sql/provider/insert-provider.sql")
    void should_updateProviderName_whenRequestValid() throws Exception {
        // When
        mockMvc.perform(put(PROVIDER_PATH + "/9001")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "OpenAI-Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then
        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM hify_provider WHERE id = 9001", String.class);
        assertThat(name).isEqualTo("OpenAI-Updated");
    }

    @Test
    @Sql(scripts = "/sql/provider/insert-provider.sql")
    void should_logicallyDeleteProvider_whenDelete() throws Exception {
        // When
        mockMvc.perform(delete(PROVIDER_PATH + "/9001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM hify_provider WHERE id = 9001", Integer.class);
        assertThat(deleted).isEqualTo(1);
    }
}
