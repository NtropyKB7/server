package com.ntropy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * 배치별 사용자 범위 설정을 로드한다.
 *
 * <p>외부 파일을 classpath 기본값보다 먼저 등록해, 배포 산출물을 다시 빌드하지 않고도
 * {@code ${NTROPY_CONFIG_DIR}/batch.properties}를 변경한 뒤 재시작하는 방식으로 배치별
 * 범위를 독립적으로 전환할 수 있다.</p>
 */
@Configuration
@PropertySources({
        @PropertySource("classpath:batch.properties"),
        @PropertySource(
                value = "file:${NTROPY_CONFIG_DIR:./config}/batch.properties",
                ignoreResourceNotFound = true
        )
})
public class BatchUserScopeConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer batchUserScopePropertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
