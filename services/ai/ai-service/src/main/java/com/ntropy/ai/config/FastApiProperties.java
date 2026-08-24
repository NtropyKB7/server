package com.ntropy.ai.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.Getter;

/** properties 파일 또는 환경변수에서 FastAPI 연결 주소를 결정합니다. */
@Getter
@Component
public class FastApiProperties {

    private final String baseUrl;

    public FastApiProperties(Environment environment) {
        String configuredBaseUrl = environment.getProperty("fastapi.base-url");
        if (isBlank(configuredBaseUrl)) {
            configuredBaseUrl = environment.getProperty("FASTAPI_BASE_URL");
        }
        if (isBlank(configuredBaseUrl)) {
            throw new IllegalStateException(
                    "FastAPI 주소가 설정되지 않았습니다. "
                            + "fastapi.base-url 또는 FASTAPI_BASE_URL을 설정하세요."
            );
        }
        this.baseUrl = configuredBaseUrl.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
