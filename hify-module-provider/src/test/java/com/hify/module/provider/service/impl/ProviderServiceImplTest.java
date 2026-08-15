package com.hify.module.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.provider.controller.dto.ProviderCreateRequest;
import com.hify.module.provider.controller.dto.ProviderResponse;
import com.hify.module.provider.repository.ProviderHealthMapper;
import com.hify.module.provider.repository.ProviderMapper;
import com.hify.module.provider.repository.ProviderModelMapper;
import com.hify.module.provider.repository.entity.AuthConfig;
import com.hify.module.provider.repository.entity.Provider;
import com.hify.module.provider.service.ProviderModelService;
import com.hify.module.provider.service.ProviderService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ProviderServiceImplTest.TestConfig.class)
class ProviderServiceImplTest {

    @MockBean
    private ProviderMapper providerMapper;

    @MockBean
    private ProviderHealthMapper providerHealthMapper;

    @MockBean
    private ProviderModelMapper providerModelMapper;

    @MockBean
    private ProviderModelService providerModelService;

    @Autowired
    private ProviderService providerService;

    @SpringBootConfiguration
    @EnableCaching
    @Import(ProviderServiceImpl.class)
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }

        @Bean
        MethodValidationPostProcessor methodValidationPostProcessor(LocalValidatorFactoryBean validator) {
            MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
            processor.setValidator(validator);
            return processor;
        }
    }

    @Test
    void should_createProvider_whenNameUnique() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setName("OpenAI 主账号");
        request.setDescription("公司主用账号");
        request.setProviderCode("openai");
        request.setBaseUrl("https://api.openai.com/v1");
        request.setPriority(10);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setApiKey("sk-test-123");
        request.setAuthConfig(authConfig);

        when(providerMapper.selectCount(any())).thenReturn(0L);
        when(providerMapper.insert(any(Provider.class))).thenAnswer(invocation -> {
            Provider entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        // When
        ProviderResponse response = providerService.create(request);

        // Then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("OpenAI 主账号");
        assertThat(response.getProviderCode()).isEqualTo("openai");
        assertThat(response.getStatus()).isEqualTo("ENABLED");
        assertThat(response.getHealthStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getPriority()).isEqualTo(10);

        ArgumentCaptor<Provider> captor = ArgumentCaptor.forClass(Provider.class);
        verify(providerMapper).insert(captor.capture());
        Provider saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("OpenAI 主账号");
        assertThat(saved.getDescription()).isEqualTo("公司主用账号");
        assertThat(saved.getProviderCode()).isEqualTo("openai");
        assertThat(saved.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(saved.getStatus()).isEqualTo("ENABLED");
        assertThat(saved.getHealthStatus()).isEqualTo("UNKNOWN");
        assertThat(saved.getPriority()).isEqualTo(10);
        assertThat(saved.getAuthConfig().getApiKey()).isEqualTo("sk-test-123");
    }

    @Test
    void should_throwProviderNameDuplicate_whenNameExists() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setName("OpenAI 主账号");
        request.setProviderCode("openai");
        when(providerMapper.selectCount(any())).thenReturn(1L);

        // When / Then
        assertThatThrownBy(() -> providerService.create(request))
                .isInstanceOf(BizException.class)
                .asInstanceOf(type(BizException.class))
                .extracting(BizException::getCode)
                .isEqualTo(ErrorCode.PROVIDER_NAME_DUPLICATE.getCode());
        verify(providerMapper, never()).insert(any(Provider.class));
    }

    @Test
    void should_throwConstraintViolation_whenNameIsNull() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setProviderCode("openai");

        // When / Then
        assertThatThrownBy(() -> providerService.create(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(providerMapper, never()).selectCount(any());
        verify(providerMapper, never()).insert(any(Provider.class));
    }

    @Test
    void should_throwApiKeyFormatError_whenApiKeyDoesNotMatchProviderFormat() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setName("OpenAI 测试账号");
        request.setProviderCode("openai");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setApiKey("invalid-key");
        request.setAuthConfig(authConfig);

        // When / Then
        assertThatThrownBy(() -> providerService.create(request))
                .isInstanceOf(BizException.class)
                .asInstanceOf(type(BizException.class))
                .extracting(BizException::getCode)
                .isEqualTo(ErrorCode.PROVIDER_API_KEY_INVALID.getCode());
        verify(providerMapper, never()).selectCount(any());
        verify(providerMapper, never()).insert(any(Provider.class));
    }
}
