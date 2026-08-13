package com.hify.module.workflow.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeConfigParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NodeConfigParser parser = new NodeConfigParser(objectMapper);

    @Test
    void should_dispatch_llm_config() throws Exception {
        NodeConfig config = parser.parse(WorkflowNodeType.LLM, """
                {"modelConfigId": 5, "prompt": "Classify: {{userMessage}}", "outputVariable": "intent"}
                """);

        assertThat(config).isInstanceOf(LlmNodeConfig.class);
        LlmNodeConfig llm = (LlmNodeConfig) config;
        assertThat(llm.modelConfigId()).isEqualTo(5L);
        assertThat(llm.prompt()).contains("{{userMessage}}");
        assertThat(llm.outputVariable()).isEqualTo("intent");
    }

    @Test
    void should_dispatch_knowledge_config() throws Exception {
        NodeConfig config = parser.parse(WorkflowNodeType.KNOWLEDGE, """
                {"knowledgeBaseId": 3, "query": "{{userMessage}}", "topK": 5, "outputVariable": "retrievedDocs"}
                """);

        assertThat(config).isInstanceOf(KnowledgeNodeConfig.class);
        KnowledgeNodeConfig knowledge = (KnowledgeNodeConfig) config;
        assertThat(knowledge.knowledgeBaseId()).isEqualTo(3L);
        assertThat(knowledge.query()).contains("{{userMessage}}");
        assertThat(knowledge.topK()).isEqualTo(5);
        assertThat(knowledge.outputVariable()).isEqualTo("retrievedDocs");
    }
}
