package com.hify.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatSseIntegrationTest extends IntegrationTestBase {

    private static final long SESSION_ID = 7004L;

    @AfterEach
    void cleanAsyncWrites() {
        jdbcTemplate.update("DELETE FROM hify_chat_message WHERE session_id = ?", SESSION_ID);
        jdbcTemplate.update("DELETE FROM hify_chat_session WHERE id = ?", SESSION_ID);
        jdbcTemplate.update("DELETE FROM hify_agent WHERE id = 7003");
        jdbcTemplate.update("DELETE FROM hify_provider_model WHERE id = 7002");
        jdbcTemplate.update("DELETE FROM hify_provider WHERE id = 7001");
    }

    @Test
    @Sql(scripts = "/sql/chat/insert-chat-chain.sql")
    void should_streamDeltaAndDone_whenPlainQuestion() throws Exception {
        // When
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", SESSION_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("content", "你好", "stream", true))))
                .andReturn();

        mvcResult.getAsyncResult(5_000);
        MvcResult dispatched = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn();

        // Then
        List<JsonNode> events = parseSseEvents(dispatched);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.path("type").asText()).isEqualTo("delta");
            assertThat(event.path("content").asText()).isNotBlank();
        });
        assertThat(events.get(events.size() - 1).path("type").asText()).isEqualTo("done");

        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT role, content FROM hify_chat_message WHERE session_id = ? ORDER BY seq", SESSION_ID);
        assertThat(messages).extracting(message -> message.get("role")).containsExactly("user", "assistant");

        String assistantContent = String.valueOf(messages.get(1).get("content"));
        String deltaContent = events.stream()
                .filter(event -> "delta".equals(event.path("type").asText()))
                .map(event -> event.path("content").asText())
                .reduce("", String::concat);
        assertThat(assistantContent).isEqualTo(deltaContent);
    }

    private List<JsonNode> parseSseEvents(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<JsonNode> events = new ArrayList<>();
        for (String line : body.split("\\R")) {
            if (line.startsWith("data:")) {
                String payload = line.substring("data:".length()).trim();
                if (!payload.isEmpty()) {
                    events.add(objectMapper.readTree(payload));
                }
            }
        }
        return events;
    }
}
