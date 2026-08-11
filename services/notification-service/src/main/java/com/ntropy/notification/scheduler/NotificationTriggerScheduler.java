package com.ntropy.notification.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.notification.service.NotificationTriggerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 방어모드/근무일지처럼 다른 도메인이 직접 알림을 생성하지 않는 이벤트를
 * 주기적으로 확인해 알림을 만드는 스케줄러.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTriggerScheduler {

    private final NotificationTriggerService notificationTriggerService;

    @Scheduled(cron = "${notification.scheduler.defense-mode-cron:0 */10 * * * ?}", zone = "Asia/Seoul")
    public void checkDefenseModeTriggers() {
        runSafely("방어모드 진입/만료임박 확인", notificationTriggerService::checkDefenseModeTriggers);
    }

    @Scheduled(cron = "${notification.scheduler.no-work-log-cron:0 30 22 * * ?}", zone = "Asia/Seoul")
    public void checkNoWorkLogToday() {
        runSafely("근무일지 미작성 확인", notificationTriggerService::checkNoWorkLogToday);
    }

    @Scheduled(cron = "${notification.scheduler.unconfirmed-work-log-cron:0 */15 * * * ?}", zone = "Asia/Seoul")
    public void checkUnconfirmedWorkLogs() {
        runSafely("근무일지 미확정 확인", notificationTriggerService::checkUnconfirmedWorkLogs);
    }

    private void runSafely(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[알림 트리거] {} 중 예상하지 못한 오류 발생", label, e);
        }
    }
}
