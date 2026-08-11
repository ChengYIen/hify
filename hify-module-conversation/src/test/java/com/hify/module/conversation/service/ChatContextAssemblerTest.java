package com.hify.module.conversation.service;

import com.hify.module.conversation.service.ChatContextCache.ContextMessage;
import com.hify.shared.agent.dto.AgentConfigDTO;
import com.hify.shared.llm.dto.LlmRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatContextAssembler 单元测试：轮数裁剪、token 预算裁剪、system_prompt 前置、默认轮数.
 */
class ChatContextAssemblerTest {

    private final ChatContextAssembler assembler = new ChatContextAssembler();

    /** 构造 n 轮交替 user/assistant 消息（时间正序）。 */
    private static List<ContextMessage> rounds(int n) {
        List<ContextMessage> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new ContextMessage("user", "问题" + i));
            list.add(new ContextMessage("assistant", "回答" + i));
        }
        return list;
    }

    private static AgentConfigDTO agentWith(Integer maxIterations, String systemPrompt) {
        return AgentConfigDTO.builder()
                .systemPrompt(systemPrompt)
                .maxIterations(maxIterations)
                .build();
    }

    @Test
    void should_default_to_three_turns_when_agent_config_absent() {
        List<LlmRequestDTO.Message> messages = assembler.assemble(1L, null, rounds(4));
        // 8 条 → 保留最近 3 轮 = 6 条（丢弃最旧 1 轮）
        assertThat(messages).hasSize(6);
        assertThat(messages.get(0).getRole()).isEqualTo("user");
        assertThat(messages.get(0).getContent()).isEqualTo("问题2");
        assertThat(messages.get(5).getContent()).isEqualTo("回答4");
    }

    @Test
    void should_use_agent_specified_max_iterations_as_turn_limit() {
        AgentConfigDTO agent = agentWith(2, null);
        List<LlmRequestDTO.Message> messages = assembler.assemble(1L, agent, rounds(4));
        // 8 条 → 保留最近 2 轮 = 4 条
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getContent()).isEqualTo("问题3");
    }

    @Test
    void should_prepend_system_prompt_when_agent_configured() {
        AgentConfigDTO agent = agentWith(null, "你是 Hify 智能客服，回答必须基于 Hify 产品");
        List<LlmRequestDTO.Message> messages = assembler.assemble(1L, agent, rounds(1));

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getRole()).isEqualTo("system");
        assertThat(messages.get(0).getContent()).isEqualTo(agent.getSystemPrompt());
        assertThat(messages.get(1).getRole()).isEqualTo("user");
        assertThat(messages.get(2).getRole()).isEqualTo("assistant");
    }

    @Test
    void should_not_prepend_system_when_agent_config_null() {
        List<LlmRequestDTO.Message> messages = assembler.assemble(1L, null, rounds(1));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo("user");
    }

    @Test
    void should_trim_by_token_budget_dropping_oldest() {
        // 6 条 × ~804 tokens = ~4824 > 3000 预算 → 应保留最新 3 条
        String big = "中".repeat(800);
        List<ContextMessage> heavy = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            heavy.add(new ContextMessage(i % 2 == 0 ? "user" : "assistant", big));
        }
        List<LlmRequestDTO.Message> messages = assembler.assemble(1L, null, heavy);
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getContent()).isEqualTo(big);
    }

    @Test
    void should_keep_newest_message_even_when_alone_over_budget() {
        String huge = "中".repeat(5000); // 单条 5004 tokens > 3000
        List<ContextMessage> heavy = List.of(new ContextMessage("user", huge));
        List<LlmRequestDTO.Message> messages = assembler.assemble(1L, null, heavy);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo(huge);
    }

    @Test
    void should_return_empty_when_no_history_and_no_agent() {
        assertThat(assembler.assemble(1L, null, List.of())).isEmpty();
    }

    @Test
    void should_resolve_max_context_turns() {
        assertThat(assembler.resolveMaxContextTurns(null)).isEqualTo(3);
        assertThat(assembler.resolveMaxContextTurns(agentWith(null, null))).isEqualTo(3);
        assertThat(assembler.resolveMaxContextTurns(agentWith(0, null))).isEqualTo(3);
        assertThat(assembler.resolveMaxContextTurns(agentWith(5, null))).isEqualTo(5);
    }
}
