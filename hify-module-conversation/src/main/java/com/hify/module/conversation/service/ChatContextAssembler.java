package com.hify.module.conversation.service;

import com.hify.common.util.TokenEstimator;
import com.hify.module.conversation.service.ChatContextCache.ContextMessage;
import com.hify.shared.agent.dto.AgentConfigDTO;
import com.hify.shared.llm.dto.LlmRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文拼装器：对会话历史做 轮数裁剪 + token 预算裁剪，并注入 system_prompt.
 *
 * <p>纯函数组件（无外部依赖），便于单元测试。裁剪顺序：</p>
 * <ol>
 *   <li><b>轮数裁剪</b>——保留最近 {@code maxTurns*2} 条（1 轮 = 1 问 + 1 答），始终保留最后 1 条；</li>
 *   <li><b>token 预算裁剪</b>——从新到旧累计估算 token，超 {@link #CONTEXT_TOKEN_BUDGET} 丢弃更老的；</li>
 *   <li><b>system_prompt</b>——Agent 配置了 systemPrompt 时前置一条 system 消息；</li>
 *   <li>输出 {@code 上下文拼装} info 日志（验收文档可 grep 的语义字段）。</li>
 * </ol>
 */
@Slf4j
@Component
public class ChatContextAssembler {

    /** 上下文 token 预算（验收 spec：token 预算 3000）。 */
    public static final int CONTEXT_TOKEN_BUDGET = 3000;
    /** 未配置 maxContextTurns 时的默认轮数。 */
    public static final int DEFAULT_MAX_CONTEXT_TURNS = 3;
    /** 预算下限：即使 system_prompt 很长也至少保留最近的几条历史。 */
    private static final int MIN_HISTORY_BUDGET = 256;

    /**
     * 解析上下文保留轮数：优先取 Agent 的 maxContextTurns（=maxIterations），未配置/无效取默认 3.
     */
    public int resolveMaxContextTurns(AgentConfigDTO agentConfig) {
        if (agentConfig != null
                && agentConfig.getMaxIterations() != null
                && agentConfig.getMaxIterations() > 0) {
            return agentConfig.getMaxIterations();
        }
        return DEFAULT_MAX_CONTEXT_TURNS;
    }

    /**
     * 拼装发给 LLM 的消息列表.
     *
     * @param sessionId   会话 ID（仅用于日志）
     * @param agentConfig Agent 配置（可能为 null=自由聊天），提供 systemPrompt / 轮数
     * @param recent      会话历史（时间正序），不会被修改
     * @return LLM 消息列表：system 在前，历史在后
     */
    public List<LlmRequestDTO.Message> assemble(Long sessionId,
                                                AgentConfigDTO agentConfig,
                                                List<ContextMessage> recent) {
        int maxTurns = resolveMaxContextTurns(agentConfig);

        String systemPrompt = agentConfig == null ? null : agentConfig.getSystemPrompt();
        boolean hasSystem = systemPrompt != null && !systemPrompt.isBlank();
        int systemTokens = hasSystem ? TokenEstimator.estimate(systemPrompt) : 0;

        List<ContextMessage> trimmed = new ArrayList<>(recent);
        trimByTurns(trimmed, maxTurns);
        int budget = Math.max(MIN_HISTORY_BUDGET, CONTEXT_TOKEN_BUDGET - systemTokens);
        int historyTokens = trimByTokens(trimmed, budget);

        List<LlmRequestDTO.Message> messages = new ArrayList<>(trimmed.size() + 1);
        if (hasSystem) {
            messages.add(LlmRequestDTO.Message.builder()
                    .role("system")
                    .content(systemPrompt)
                    .build());
        }
        for (ContextMessage m : trimmed) {
            messages.add(LlmRequestDTO.Message.builder()
                    .role(m.role())
                    .content(m.content())
                    .build());
        }

        log.info("上下文拼装: sessionId={}, 历史消息{}条, token预算{}, 保留最近{}轮, 裁剪后{}条, 含systemPrompt={}, 预估{}tokens",
                sessionId, recent.size(), CONTEXT_TOKEN_BUDGET, maxTurns,
                trimmed.size(), hasSystem, historyTokens + systemTokens);
        return messages;
    }

    /** 轮数裁剪：丢弃头部，剩余 ≤ maxTurns*2 条（最少保留 2 条）。 */
    private void trimByTurns(List<ContextMessage> list, int maxTurns) {
        int cap = Math.max(1, maxTurns) * 2;
        if (list.size() > cap) {
            list.subList(0, list.size() - cap).clear();
        }
    }

    /**
     * token 预算裁剪：从新到旧累计，超预算丢弃更老的（始终保留最新 1 条）.
     *
     * @return 保留历史的估算 token 数
     */
    private int trimByTokens(List<ContextMessage> list, int budget) {
        int total = 0;
        int keepStart = list.size();
        for (int i = list.size() - 1; i >= 0; i--) {
            int cost = TokenEstimator.estimate(list.get(i).content());
            if (i != list.size() - 1 && total + cost > budget) {
                break;
            }
            total += cost;
            keepStart = i;
        }
        if (keepStart > 0) {
            list.subList(0, keepStart).clear();
        }
        return total;
    }
}
