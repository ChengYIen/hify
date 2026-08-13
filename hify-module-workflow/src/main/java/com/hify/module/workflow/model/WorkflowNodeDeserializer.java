package com.hify.module.workflow.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Deserializes a {@link WorkflowNode} so the type discriminator stays on the
 * node itself while {@code config} remains a plain JSON object without a
 * redundant {@code type} field.
 */
public class WorkflowNodeDeserializer extends StdDeserializer<WorkflowNode> {

    public WorkflowNodeDeserializer() {
        super(WorkflowNode.class);
    }

    @Override
    public WorkflowNode deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper objectMapper = (ObjectMapper) parser.getCodec();
        JsonNode root = objectMapper.readTree(parser);

        WorkflowNode node = new WorkflowNode();
        node.setNodeKey(text(root, "nodeKey"));
        node.setName(text(root, "name"));

        JsonNode position = root.get("position");
        if (position != null && position.isObject()) {
            node.setPosition(objectMapper.treeToValue(position, WorkflowNodePosition.class));
        }

        String typeValue = text(root, "type");
        if (typeValue == null) {
            throw context.weirdStringException("", WorkflowNodeType.class, "node type is required");
        }
        WorkflowNodeType type = resolveType(context, typeValue);
        node.setType(type);

        JsonNode configNode = root.get("config");
        if (configNode != null && !configNode.isNull()) {
            NodeConfig config = new NodeConfigParser(objectMapper).parse(type, configNode.toString());
            node.setConfig(config);
        }
        return node;
    }

    private WorkflowNodeType resolveType(DeserializationContext context, String typeValue) throws IOException {
        try {
            return WorkflowNodeType.valueOf(typeValue);
        } catch (IllegalArgumentException e) {
            throw context.weirdStringException(typeValue, WorkflowNodeType.class, "unknown workflow node type");
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
