package com.ntropy.work.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.work.service.SettlementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 정산 배치를 실행하는 스케줄러입니다.
 *
 * 실제 처리는 SettlementService.runDailyBatch()에 맡기고,
 * 이 클래스는 정해진 시각에 배치를 시작하는 역할만 담당합니다.
 *
 * account-service의 Codef 계좌 동기화 배치가 매일 새벽 1시쯤 끝나는 것을 전제로,
 * 그 이후 시각(새벽 1시 30분)에 실행되도록 기본값을 잡았습니다. Codef 배치 완료 시각이
 * 바뀌면 settlement.scheduler.daily-cron 설정으로 조정하면 됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;

    /**
     * 매일 새벽 1시 30분, 한국 시간 기준으로 실행됩니다.
     *
     * 기본 크론 표현식:
     * 초 분 시 일 월 요일
     * 0  30 1  *  *  ?
     */
    @Scheduled(
            cron = "${settlement.scheduler.daily-cron:0 30 1 * * ?}",
            zone = "Asia/Seoul"
    )
    public void runDailySettlementBatch() {
        log.info("[정산 배치] 일일 정산 배치 스케줄 실행 시작 (실행시각: {})", LocalDateTime.now());

        try {
            settlementService.runDailyBatch();
            log.info("[정산 배치] 일일 정산 배치 스케줄 실행 완료 (실행시각: {})", LocalDateTime.now());
        } catch (Exception exception) {
            // 예상하지 못한 예외가 발생해도 스케줄러 스레드가 죽지 않도록 기록합니다.
            log.error("[정산 배치] 일일 정산 배치 스케줄 실행 중 예상하지 못한 오류 발생 (실행시각: {})",
                    LocalDateTime.now(), exception);
        }
    }
}
