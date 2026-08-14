package com.hify.module.workflow.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {

    @Test
    void should_prewrite_user_message_for_start_node() {
        ExecutionContext context = new ExecutionContext(1L, "7天内能退货吗");

        assertThat(context.get("start", "userMessage")).isEqualTo("7天内能退货吗");
    }

    @Test
    void should_set_and_get_variables_with_node_key_prefix() {
        ExecutionContext context = new ExecutionContext(1L, "hello");

        context.set("classify", "intent", "售后");

        assertThat(context.get("classify", "intent")).isEqualTo("售后");
        assertThat(context.snapshot()).containsEntry("classify.intent", "售后");
    }

    @Test
    void should_resolve_placeholders_and_keep_missing_ones() {
        ExecutionContext context = new ExecutionContext(1L, "7天内能退货吗");
        context.set("router", "route", "售后");

        String resolved = context.resolve(
                "问题={{start.userMessage}}, 路由={{router.route}}, 缺失={{missing.value}}");

        assertThat(resolved)
                .isEqualTo("问题=7天内能退货吗, 路由=售后, 缺失={{missing.value}}");
    }

    @Test
    void should_resolve_intent_placeholder_in_greeting() {
        ExecutionContext context = new ExecutionContext(1L, "7天内能退货吗");
        context.set("classify", "intent", "售后");

        String resolved = context.resolve("你好，{{classify.intent}}客服为您服务");

        assertThat(resolved).isEqualTo("你好，售后客服为您服务");
    }

    @Test
    void snapshot_should_be_read_only() {
        ExecutionContext context = new ExecutionContext(1L, "hello");

        Map<String, Object> snapshot = context.snapshot();

        assertThatThrownBy(() -> snapshot.put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
