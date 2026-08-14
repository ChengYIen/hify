package com.hify.module.conversation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.module.conversation.controller.dto.ChatMessageResponse;
import com.hify.module.conversation.controller.dto.ChatSessionResponse;
import com.hify.module.conversation.service.ChatContextAssembler;
import com.hify.module.conversation.service.ChatContextCache;
import com.hify.module.conversation.service.ChatMessageService;
import com.hify.module.conversation.service.ChatSessionService;
import com.hify.module.workflow.engine.WorkflowEngine;
import com.hify.shared.agent.AgentConfigApi;
import com.hify.shared.agent.dto.AgentConfigDTO;
import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.LlmStreamCallback;
import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;
import com.hify.shared.rag.RagRetrievalApi;
import com.hify.shared.tool.McpToolQueryApi;
import com.hify.shared.tool.ToolExecutionApi;
import com.hify.shared.tool.dto.AgentBoundToolDTO;
import com.hify.shared.tool.dto.ToolResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatServiceImpl MCP 工具调用流单测：首次非流式获取 tool_calls → 执行工具 → 二次流式推送.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplToolTest {

    private static final String TOOL_CALLS_JSON = """
            [{"id":"call_1","type":"function","function":{"name":"query_order","arguments":"{\\"orderId\\":\\"A1\\"}"}}]
            """.strip();

    @Mock
    private ChatSessionService chatSessionService;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private LlmProviderApi llmProviderApi;
    @Mock
    private AgentConfigApi agentConfigApi;
    @Mock
    private RagRetrievalApi ragRetrievalApi;
    @Mock
    private McpToolQueryApi mcpToolQueryApi;
    @Mock
    private ToolExecutionApi toolExecutionApi;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private ChatContextCache chatContextCache;
    @Mock
    private ThreadPoolTaskExecutor llmExecutor;
    @Mock
    private ScheduledThreadPoolExecutor heartbeatScheduler;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                chatSessionService, chatMessageService, llmProviderApi, agentConfigApi,
                ragRetrievalApi, mcpToolQueryApi, toolExecutionApi, workflowEngine,
                chatContextCache, new ChatContextAssembler(), llmExecutor,
                heartbeatScheduler, new ObjectMapper());
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(llmExecutor).submit(any(Runnable.class));
    }

    @Test
    void shouldExecuteMcpToolThenStreamFinalAnswer() {
        mockBase();
        when(llmProviderApi.chat(any(LlmRequestDTO.class))).thenReturn(LlmResponseDTO.builder()
                .model("gpt-4o")
                .content("")
                .finishReason("tool_calls")
                .toolCalls(TOOL_CALLS_JSON)
                .build());
        when(toolExecutionApi.execute(eq(10L), eq("query_order"), any()))
                .thenReturn(ToolResultDTO.builder()
                        .success(true)
                        .content("订单 A1 已发货")
                        .build());
        when(chatMessageService.createAssistantMessage(anyLong(), anyString(), anyString(),
                any(), anyString(), any())).thenReturn(ChatMessageResponse.builder().id(2L).build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        ArgumentCaptor<LlmRequestDTO> firstCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi).chat(firstCaptor.capture());
        LlmRequestDTO firstRequest = firstCaptor.getValue();
        assertThat(firstRequest.isStream()).isFalse();
        assertThat(firstRequest.getTools()).hasSize(1);
        assertThat(firstRequest.getTools().get(0).getFunction().getName()).isEqualTo("query_order");

        ArgumentCaptor<LlmRequestDTO> secondCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        ArgumentCaptor<LlmStreamCallback> callbackCaptor =
                ArgumentCaptor.forClass(LlmStreamCallback.class);
        verify(llmProviderApi).streamChat(secondCaptor.capture(), callbackCaptor.capture());
        LlmRequestDTO secondRequest = secondCaptor.getValue();
        assertThat(secondRequest.isStream()).isTrue();
        assertThat(secondRequest.getTools()).isNull();
        assertThat(secondRequest.getMessages())
                .extracting(LlmRequestDTO.Message::getRole)
                .containsExactly("system", "assistant", "tool");
        assertThat(secondRequest.getMessages().get(1).getToolCalls()).hasSize(1);
        assertThat(secondRequest.getMessages().get(2).getToolCallId()).isEqualTo("call_1");
        assertThat(secondRequest.getMessages().get(2).getContent()).isEqualTo("订单 A1 已发货");

        callbackCaptor.getValue().onComplete(LlmResponseDTO.builder()
                .content("您的订单已发货")
                .model("gpt-4o")
                .finishReason("stop")
                .build());
        verify(chatMessageService).createAssistantMessage(
                1L, "您的订单已发货", "gpt-4o", null, "stop", null);
    }

    @Test
    void shouldFeedToolErrorBackToLlmWithoutInterrupting() {
        mockBase();
        when(llmProviderApi.chat(any(LlmRequestDTO.class))).thenReturn(LlmResponseDTO.builder()
                .model("gpt-4o")
                .content("")
                .finishReason("tool_calls")
                .toolCalls(TOOL_CALLS_JSON)
                .build());
        when(toolExecutionApi.execute(eq(10L), eq("query_order"), any()))
                .thenReturn(ToolResultDTO.builder()
                        .success(false)
                        .errorMessage("MCP 服务器连接失败")
                        .build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        ArgumentCaptor<LlmRequestDTO> secondCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi).streamChat(secondCaptor.capture(), any(LlmStreamCallback.class));
        assertThat(secondCaptor.getValue().getMessages().get(2).getContent())
                .contains("MCP 服务器连接失败");
    }

    @Test
    void shouldUseOriginalFlowWhenNoToolsBound() {
        mockBase();
        when(mcpToolQueryApi.listBoundTools(2L)).thenReturn(List.of());

        chatService.sendMessage(1L, "普通问题", 2L);

        verify(llmProviderApi, never()).chat(any());
        ArgumentCaptor<LlmRequestDTO> requestCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi).streamChat(requestCaptor.capture(), any(LlmStreamCallback.class));
        assertThat(requestCaptor.getValue().isStream()).isTrue();
        assertThat(requestCaptor.getValue().getTools()).isNull();
    }

    private void mockBase() {
        ChatSessionResponse session = ChatSessionResponse.builder()
                .id(1L)
                .modelId(100L)
                .agentId(2L)
                .build();
        when(chatSessionService.getById(1L)).thenReturn(session);
        when(agentConfigApi.getAgentConfig(2L)).thenReturn(AgentConfigDTO.builder()
                .id(2L)
                .systemPrompt("你是客服助手")
                .modelId(100L)
                .temperature(0.7)
                .maxTokens(1024)
                .build());
        when(chatContextCache.readRecent(1L)).thenReturn(List.of());
        when(chatMessageService.listBySession(1L, 20)).thenReturn(List.of());
        when(mcpToolQueryApi.listBoundTools(2L)).thenReturn(List.of(
                AgentBoundToolDTO.builder()
                        .toolName("query_order")
                        .description("查询订单")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"string\"}}}")
                        .mcpServerId(10L)
                        .build()));
    }
}
