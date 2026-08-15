package com.hify.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.util.JwtUtil;
import com.hify.module.provider.scheduler.ProviderHealthCheckScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * Hify 集成测试基类：mock profile（H2 内存库），完整启动 Spring Boot，
 * 外部 LLM 由 MockProviderAdapter 替换，定时健康检查打桩避免真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    @Qualifier("mysqlDataSource")
    protected DataSource mysqlDataSource;

    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected ProviderHealthCheckScheduler providerHealthCheckScheduler;

    protected String token;

    @BeforeEach
    void setUpTokenAndRedis() {
        token = JwtUtil.generate(1L, "integration", "USER");
        jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        try {
            stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        } catch (Exception ignored) {
            // Redis 不可用时缓存链路自动降级，不影响业务断言
        }
    }

    protected JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
