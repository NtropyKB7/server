package com.ntropy.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/** FastAPI 연결 주소를 공통 설정과 외부 설정에서 로드합니다. */
@Configuration
@PropertySources({
        @PropertySource(
                value = "classpath:fastapi.properties",
                ignoreResourceNotFound = true
        ),
        @PropertySource(
                value = "file:${NTROPY_CONFIG_DIR:./config}/fastapi.properties",
                ignoreResourceNotFound = true
        )
})
public class FastApiConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer fastApiPropertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
