INSERT INTO hify_provider (id, name, description, provider_code, base_url, status, health_status, discovery_type, priority, created_at, updated_at, deleted)
VALUES (7001, 'OpenAI-Chat', 'chat integration provider', 'openai', 'https://api.openai.com/v1', 'ENABLED', 'UNKNOWN', 'MANUAL', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO hify_provider_model (id, provider_id, model_name, display_name, model_type, context_window, max_output, supports_vision, supports_tools, supports_streaming, priority, status, created_at, updated_at, deleted)
VALUES (7002, 7001, 'gpt-chat-test', 'Chat Test', 'LLM', 8192, 4096, 0, 0, 1, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO hify_agent (id, name, description, system_prompt, model_id, temperature, max_tokens, max_iterations, tools_enabled, status, created_at, updated_at, deleted)
VALUES (7003, 'Chat-Agent', 'chat agent', 'You are a helpful assistant.', 7002, 0.70, 1024, 3, 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO hify_chat_session (id, title, user_id, agent_id, model_id, status, message_count, total_tokens, created_at, updated_at, deleted)
VALUES (7004, 'Chat-IT', 1, 7003, 7002, 'ACTIVE', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
