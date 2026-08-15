package com.hify.integration;

import com.hify.integration.support.MockProviderAdapter;
import com.hify.shared.llm.dto.LlmRequestDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatContextIntegrationTest extends IntegrationTestBase {

    private static final long SESSION_ID = 7004L;

    @Autowired
    private MockProviderAdapter mockProviderAdapter;

    @BeforeEach
    void clearMockRequests() {
        mockProviderAdapter.clearRequests();
    }

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
    void should_keepHistoryAscending_whenThirdMessageSent() throws Exception {
        // When
        sendMessage("第一条");
        sendMessage("第二条");
        sendMessage("第三条");

        // Then
        List<LlmRequestDTO> requests = mockProviderAdapter.getRequests();
        assertThat(requests).hasSize(3);

        List<LlmRequestDTO.Message> thirdMessages = requests.get(2).getMessages();
        assertThat(thirdMessages)
                .extracting(LlmRequestDTO.Message::getContent)
                .contains("第一条", "第二条", "第三条");
        assertThat(thirdMessages)
                .extracting(LlmRequestDTO.Message::getRole)
                .containsSubsequence("user", "assistant", "user", "assistant", "user");

        LlmRequestDTO.Message last = thirdMessages.get(thirdMessages.size() - 1);
        assertThat(last.getRole()).isEqualTo("user");
        assertThat(last.getContent()).isEqualTo("第三条");
    }

    private void sendMessage(String content) throws Exception {
        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", SESSION_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("content", content))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
