package com.ntropy.account.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * DAILY_BATCH_EXECUTION lease 유지 시간 정책 (이슈 #158).
 * 별도 heartbeat 스레드를 두지 않고 사용자·기관 처리 루프마다 heartbeat로 lease_until을 연장하는
 * 것을 전제로 하므로, CODEF 단일 요청의 최대 read timeout(로컬 DEMO 설정 기준 최대 310초,
 * {@link CodefProperties})보다 충분한 여유를 두고 길게 잡는다. 평균 응답시간 기준으로 잡으면
 * 느린 단건 호출 하나만으로도 heartbeat 전에 lease가 만료될 수 있으므로 사용하지 않는다.
 */
@Getter
@Component
public class DailySyncLeaseProperties {

    private final Duration leaseDuration;

    public DailySyncLeaseProperties(
            @Value("${daily-sync.lease.duration-seconds:360}") long leaseDurationSeconds
    ) {
        if (leaseDurationSeconds <= 0) {
            throw new IllegalStateException("daily-sync.lease.duration-seconds는 양수여야 합니다");
        }
        this.leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    }
}
