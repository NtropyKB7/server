package com.ntropy.account.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 가상 데이터 생성 등 "오늘"을 기준일로 쓰는 로직이 고정 Clock으로 테스트 가능하도록 빈으로 분리한다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
