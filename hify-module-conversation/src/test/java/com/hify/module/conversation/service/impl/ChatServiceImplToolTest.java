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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(llmExecutor).submit(any(Runnable.class));
    }

    @Test
    void shouldExecuteMcpToolThenStreamFinalAnswer() {
        mockBase();
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder()
                        .model("gpt-4o")
                        .content("")
                        .finishReason("tool_calls")
                        .toolCalls(TOOL_CALLS_JSON)
                        .build())
                .thenReturn(LlmResponseDTO.builder()
                        .content("您的订单已发货")
                        .model("gpt-4o")
                        .finishReason("stop")
                        .build());
        when(toolExecutionApi.execute(eq(10L), eq("query_order"), any()))
                .thenReturn(ToolResultDTO.builder()
                        .success(true)
                        .content("订单 A1 已发货")
                        .build());
        when(chatMessageService.createAssistantMessage(anyLong(), anyString(), anyString(),
                any(), anyString(), any())).thenReturn(ChatMessageResponse.builder().id(2L).build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        ArgumentCaptor<LlmRequestDTO> requestCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi, times(2)).chat(requestCaptor.capture());
        List<LlmRequestDTO> requests = requestCaptor.getAllValues();
        LlmRequestDTO firstRequest = requests.get(0);
        assertThat(firstRequest.isStream()).isFalse();
        assertThat(firstRequest.getTools()).hasSize(1);
        assertThat(firstRequest.getTools().get(0).getFunction().getName()).isEqualTo("query_order");

        LlmRequestDTO secondRequest = requests.get(1);
        assertThat(secondRequest.isStream()).isFalse();
        assertThat(secondRequest.getTools()).hasSize(1);
        assertThat(secondRequest.getMessages())
                .extracting(LlmRequestDTO.Message::getRole)
                .containsExactly("system", "assistant", "tool");
        assertThat(secondRequest.getMessages().get(1).getToolCalls()).hasSize(1);
        assertThat(secondRequest.getMessages().get(2).getToolCallId()).isEqualTo("call_1");
        assertThat(secondRequest.getMessages().get(2).getContent()).isEqualTo("订单 A1 已发货");

        verify(chatMessageService).createAssistantMessage(
                1L, "您的订单已发货", "gpt-4o", null, "stop", null);
        verify(llmProviderApi, never()).streamChat(any(), any());
    }

    @Test
    void shouldFeedToolErrorBackToLlmWithoutInterrupting() {
        mockBase();
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder()
                        .model("gpt-4o")
                        .content("")
                        .finishReason("tool_calls")
                        .toolCalls(TOOL_CALLS_JSON)
                        .build())
                .thenReturn(LlmResponseDTO.builder()
                        .content("done")
                        .model("gpt-4o")
                        .finishReason("stop")
                        .build());
        when(toolExecutionApi.execute(eq(10L), eq("query_order"), any()))
                .thenReturn(ToolResultDTO.builder()
                        .success(false)
                        .errorMessage("MCP 服务器连接失败")
                        .build());
        when(chatMessageService.createAssistantMessage(anyLong(), anyString(), anyString(),
                any(), anyString(), any())).thenReturn(ChatMessageResponse.builder().id(2L).build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        ArgumentCaptor<LlmRequestDTO> requestCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi, times(2)).chat(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).getMessages().get(2).getContent())
                .contains("MCP 服务器连接失败");
        verify(llmProviderApi, never()).streamChat(any(), any());
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

    @Test
    void shouldUseStreamFlow_whenToolsDisabled() {
        mockBase();
        when(agentConfigApi.getAgentConfig(2L)).thenReturn(AgentConfigDTO.builder()
                .id(2L)
                .modelId(100L)
                .toolsEnabled(false)
                .build());

        chatService.sendMessage(1L, "普通问题", 2L);

        ArgumentCaptor<LlmRequestDTO> requestCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi).streamChat(requestCaptor.capture(), any(LlmStreamCallback.class));
        assertThat(requestCaptor.getValue().isStream()).isTrue();
        assertThat(requestCaptor.getValue().getTools()).isNull();
        verify(llmProviderApi, never()).chat(any());
        verify(mcpToolQueryApi, never()).listBoundTools(anyLong());
    }

    @Test
    void shouldFeedUnboundToolMessageBackToLlm_whenToolNotBound() {
        mockBase();
        String unboundToolCallsJson = """
                [{"id":"call_1","type":"function","function":{"name":"hack_tool","arguments":"{}"}}]
                """.strip();
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder()
                        .model("gpt-4o")
                        .content("")
                        .finishReason("tool_calls")
                        .toolCalls(unboundToolCallsJson)
                        .build())
                .thenReturn(LlmResponseDTO.builder()
                        .content("done")
                        .model("gpt-4o")
                        .finishReason("stop")
                        .build());
        when(chatMessageService.createAssistantMessage(anyLong(), anyString(), anyString(),
                any(), anyString(), any()))
                .thenReturn(ChatMessageResponse.builder().id(2L).content("答案").build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        ArgumentCaptor<LlmRequestDTO> requestCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi, times(2)).chat(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).getMessages().get(2).getContent())
                .contains("未绑定");
        verify(toolExecutionApi, never()).execute(anyLong(), anyString(), any());
    }

    @Test
    void shouldPassEmptyArguments_whenArgumentsJsonMalformed() {
        mockBase();
        String badArgsToolCallsJson = """
                [{"id":"call_1","type":"function","function":{"name":"query_order","arguments":"{bad json"}}]
                """.strip();
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder()
                        .model("gpt-4o")
                        .content("")
                        .finishReason("tool_calls")
                        .toolCalls(badArgsToolCallsJson)
                        .build())
                .thenReturn(LlmResponseDTO.builder()
                        .content("done")
                        .model("gpt-4o")
                        .finishReason("stop")
                        .build());
        when(toolExecutionApi.execute(eq(10L), eq("query_order"), any()))
                .thenReturn(ToolResultDTO.builder().success(true).content("ok").build());
        when(chatMessageService.createAssistantMessage(anyLong(), anyString(), anyString(),
                any(), anyString(), any())).thenReturn(ChatMessageResponse.builder().id(2L).build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(toolExecutionApi).execute(eq(10L), eq("query_order"), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).isEmpty();
    }

    @Test
    void shouldStopAfterMaxIterations_whenLlmKeepsCallingTools() {
        mockBase();
        when(agentConfigApi.getAgentConfig(2L)).thenReturn(AgentConfigDTO.builder()
                .id(2L)
                .modelId(100L)
                .maxIterations(2)
                .build());
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder()
                        .model("gpt-4o")
                        .content("")
                        .finishReason("tool_calls")
                        .toolCalls(TOOL_CALLS_JSON)
                        .build());
        when(toolExecutionApi.execute(eq(10L), eq("query_order"), any()))
                .thenReturn(ToolResultDTO.builder().success(true).content("ok").build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        verify(llmProviderApi, times(2)).chat(any(LlmRequestDTO.class));
        verify(toolExecutionApi, times(2)).execute(eq(10L), eq("query_order"), any());
        verify(chatMessageService, never()).createAssistantMessage(
                anyLong(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void shouldPersistFallbackAnswer_whenToolCallsJsonMalformed() {
        mockBase();
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder()
                        .model("gpt-4o")
                        .content("fallback-content")
                        .finishReason("tool_calls")
                        .toolCalls("not-json")
                        .build());
        when(chatMessageService.createAssistantMessage(anyLong(), anyString(), anyString(),
                any(), anyString(), any())).thenReturn(ChatMessageResponse.builder().id(2L).build());

        chatService.sendMessage(1L, "查一下订单", 2L);

        verify(chatMessageService).createAssistantMessage(
                eq(1L), eq("fallback-content"), eq("gpt-4o"), any(), anyString(), any());
        verify(toolExecutionApi, never()).execute(anyLong(), anyString(), any());
    }

    @Test
    void shouldFallbackToNoKnowledgeContext_whenRagSearchFails() {
        mockBase();
        when(agentConfigApi.getAgentConfig(2L)).thenReturn(AgentConfigDTO.builder()
                .id(2L)
                .modelId(100L)
                .knowledgeIds("[1]")
                .build());
        when(chatMessageService.listBySession(1L, 20)).thenReturn(List.of(
                ChatMessageResponse.builder().role("user").content("退货政策是什么").build()));
        when(ragRetrievalApi.search(anyLong(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("rag down"));
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder()
                        .content("答案")
                        .model("gpt-4o")
                        .finishReason("stop")
                        .build());
        when(chatMessageService.createAssistantMessage(anyLong(), anyString(), anyString(),
                any(), anyString(), any()))
                .thenReturn(ChatMessageResponse.builder().id(2L).content("答案").build());

        ChatMessageResponse response = chatService.sendBlocking(1L, "退货政策是什么", 2L);

        assertThat(response.getContent()).isEqualTo("答案");
        ArgumentCaptor<LlmRequestDTO> requestCaptor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMessages())
                .anyMatch(message -> "system".equals(message.getRole())
                        && message.getContent().contains("未检索到任何参考资料"));
    }

    @Test
    void shouldFinishStream_whenStreamChatReportsError() {
        mockBase();
        when(mcpToolQueryApi.listBoundTools(2L)).thenReturn(List.of());
        doAnswer(invocation -> {
            LlmStreamCallback callback = invocation.getArgument(1);
            callback.onError("上游服务异常");
            return null;
        }).when(llmProviderApi).streamChat(any(LlmRequestDTO.class), any(LlmStreamCallback.class));

        chatService.sendMessage(1L, "普通问题", 2L);

        verify(llmProviderApi).streamChat(any(LlmRequestDTO.class), any(LlmStreamCallback.class));
        verify(llmProviderApi, never()).chat(any());
        verify(chatMessageService, never()).createAssistantMessage(
                anyLong(), anyString(), anyString(), any(), anyString(), any());
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
        lenient().when(chatMessageService.listBySession(1L, 20)).thenReturn(List.of());
        lenient().when(mcpToolQueryApi.listBoundTools(2L)).thenReturn(List.of(
                AgentBoundToolDTO.builder()
                        .toolName("query_order")
                        .description("查询订单")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"string\"}}}")
                        .mcpServerId(10L)
                        .build()));
    }
}
