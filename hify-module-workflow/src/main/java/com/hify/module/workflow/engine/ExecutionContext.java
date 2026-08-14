package com.hify.module.workflow.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-run variable container shared by all workflow nodes.
 *
 * <p>Variables are keyed as {@code nodeKey.varName} and kept in insertion
 * order. The constructor pre-writes {@code start.userMessage} so every node
 * can resolve the user message without extra setup.</p>
 */
public class ExecutionContext {

    private final Long workflowRunId;
    private final Map<String, Object> variables = new LinkedHashMap<>();

    public ExecutionContext(Long workflowRunId, String userMessage) {
        this.workflowRunId = workflowRunId;
        this.variables.put("start.userMessage", userMessage);
    }

    public Long getWorkflowRunId() {
        return workflowRunId;
    }

    public void set(String nodeKey, String varName, Object value) {
        variables.put(nodeKey + "." + varName, value);
    }

    public Object get(String nodeKey, String varName) {
        return variables.get(nodeKey + "." + varName);
    }

    /**
     * Replaces every {@code {{nodeKey.varName}}} placeholder with the stored
     * value. Missing variables and null values keep the original placeholder.
     */
    public String resolve(String template) {
        if (template == null) {
            return null;
        }
        String resolved = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            resolved = resolved.replace("{{" + entry.getKey() + "}}", String.valueOf(value));
        }
        return resolved;
    }

    /**
     * Defensive copy wrapped as an unmodifiable map, safe for execution-record
     * persistence even if the run keeps mutating variables afterwards.
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
}
