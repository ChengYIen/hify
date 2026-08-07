package com.hify.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置.
 * <p>
 * Java 8 时间类型统一输出格式，关闭数字时间戳模式。
 * Spring Boot 自动装配时会拾取此配置合并到默认 ObjectMapper。
 * </p>
 */
@Configuration
public class JacksonConfig {

    /** LocalDateTime: ISO 8601 格式 */
    static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** LocalDate: 日期格式 */
    static final DateTimeFormatter LOCAL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // 注册 JavaTimeModule，支持 LocalDateTime / LocalDate / LocalTime
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(LOCAL_DATE_TIME_FORMATTER));
            javaTimeModule.addSerializer(LocalDate.class,
                    new LocalDateSerializer(LOCAL_DATE_FORMATTER));

            builder.modules(javaTimeModule);

            // 关掉时间戳数字输出，改用字符串格式
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
