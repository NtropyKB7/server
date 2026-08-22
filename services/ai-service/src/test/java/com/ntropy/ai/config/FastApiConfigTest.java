package com.ntropy.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

import com.ntropy.ai.client.fastapi.FastApiProductRecommendationClient;
import com.ntropy.ai.client.fastapi.FastApiTransactionClassificationClient;

class FastApiConfigTest {

    @Test
    void loadsBaseUrlFromEnvironmentVariableForBothFastApiClients() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            String expectedBaseUrl = "https://fastapi.example.test";

            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "fastApiEnvironment",
                            Map.of("FASTAPI_BASE_URL", expectedBaseUrl)
                    )
            );
            context.register(
                    TestPlaceholderConfig.class,
                    FastApiProductRecommendationClient.class,
                    FastApiTransactionClassificationClient.class
            );
            context.refresh();

            assertEquals(
                    expectedBaseUrl,
                    getField(
                            context.getBean(FastApiProductRecommendationClient.class),
                            "fastApiBaseUrl"
                    )
            );
            assertEquals(
                    expectedBaseUrl,
                    getField(
                            context.getBean(FastApiTransactionClassificationClient.class),
                            "fastApiBaseUrl"
                    )
            );
        }
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    @Configuration
    static class TestPlaceholderConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
