package com.ntropy.ai.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.ai.service.MonthlyAiReportOrchestrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매월 AI 리포트 배치를 실행하는 스케줄러입니다.
 *
 * 실제 처리 순서는 MonthlyAiReportOrchestrationService에 맡기고,
 * 이 클래스는 정해진 시각에 배치를 시작하는 역할만 담당합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyAiReportScheduler {

    // 월간 리포트 전체 흐름을 처리하는 서비스입니다.
    private final MonthlyAiReportOrchestrationService orchestrationService;

    /**
     * 매월 1일 자정, 한국 시간 기준으로 실행됩니다.
     *
     * 기본 크론 표현식:
     * 초 분 시 일 월 요일
     * 0  0  0  1  *  ?
     */
    @Scheduled(
            cron = "${ai-report.scheduler.monthly-cron:0 0 0 1 * ?}",
            zone = "Asia/Seoul"
    )
    public void runMonthlyAiReportBatch() {
        log.info("[AI 리포트 배치] 월간 배치 스케줄 실행 시작");

        try {
            orchestrationService.runLastMonthBatch();

            log.info("[AI 리포트 배치] 월간 배치 스케줄 실행 완료");
        } catch (Exception exception) {
            // 예상하지 못한 예외가 발생해도 스케줄러 스레드가 죽지 않도록 기록합니다.
            log.error(
                    "[AI 리포트 배치] 월간 배치 스케줄 실행 중 예상하지 못한 오류 발생",
                    exception
            );
        }
    }
}