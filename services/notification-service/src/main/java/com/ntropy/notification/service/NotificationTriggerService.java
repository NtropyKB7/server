package com.ntropy.notification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.CalendarQueryClient;
import com.ntropy.common.client.DefenseModeQueryClient;
import com.ntropy.common.dto.defense.summary.DefenseModeSummary;
import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarWorkBrief;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 다른 도메인이 알림 생성을 직접 호출하지 않는 이벤트(방어모드, 근무일지)를
 * QueryClient로 폴링해서 알림을 만드는 서비스. 스케줄러가 주기적으로 호출한다.
 *
 * 방어모드 만료 임박 기준(D-Day 3 이하), 근무일지 미확정 리마인더 지연(종료 30분 후)은
 * 정해진 스펙이 없어 임의로 정한 값이라 필요하면 조정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTriggerService {

    private static final int DEFENSE_MODE_DDAY_WARNING_THRESHOLD = 3;
    private static final int WORK_LOG_REMINDER_DELAY_MINUTES = 30;
    private static final String DEFENSE_MODE_ACTIVE_STATUS = "ACTIVE";
    private static final String WORK_LOG_PLANNED_STATUS = "PLANNED";

    private final ActiveUserQueryClient activeUserQueryClient;
    private final DefenseModeQueryClient defenseModeQueryClient;
    private final CalendarQueryClient calendarQueryClient;
    private final NotificationService notificationService;

    /**
     * 활성 유저 전체를 돌며 방어모드 진입/만료임박을 확인한다.
     * DefenseModeQueryClient에 유저 단위 조회만 있어 활성 유저마다 호출하고,
     * 방어모드가 아닌 유저는 예외로 구분한다(defense-service에 벌크 조회가 추가되면 대체 예정).
     */
    public void checkDefenseModeTriggers() {
        for (Long userId : activeUserQueryClient.findActiveUserIds()) {
            DefenseModeSummary summary;
            try {
                summary = defenseModeQueryClient.getCurrent(userId);
            } catch (ServiceException e) {
                continue;
            }
            if (summary == null || !DEFENSE_MODE_ACTIVE_STATUS.equals(summary.getStatus())) {
                continue;
            }

            notificationService.createNotification(
                    userId,
                    "defense-enter-" + summary.getDefenseId(),
                    "DEFENSE_MODE",
                    "방어모드가 시작되었습니다",
                    "오늘부터 방어모드가 적용됩니다."
            );

            Integer dDay = summary.getDDay();
            if (dDay != null && dDay <= DEFENSE_MODE_DDAY_WARNING_THRESHOLD) {
                notificationService.createNotification(
                        userId,
                        "defense-dday-warning-" + summary.getDefenseId() + "-" + LocalDate.now(),
                        "DEFENSE_MODE",
                        "방어모드 종료가 얼마 남지 않았습니다",
                        "생존 가능 기간이 " + dDay + "일 남았습니다."
                );
            }
        }
    }

    /** 매일 지정된 시각에 실행. 오늘 근무 기록이 없는 유저에게 리마인더를 보낸다. */
    public void checkNoWorkLogToday() {
        LocalDate today = LocalDate.now();
        for (Long userId : activeUserQueryClient.findActiveUserIds()) {
            CalendarDailySummary summary = calendarQueryClient.getDailySummary(userId, today, null, null);
            if (summary.getWorks().isEmpty()) {
                notificationService.createNotification(
                        userId,
                        "worklog-noWork-" + userId + "-" + today,
                        "WORK",
                        "오늘 근무 기록이 없어요",
                        "오늘 일하셨다면 근무일지를 작성해 주세요."
                );
            }
        }
    }

    /** 주기적으로 실행. 종료 후 일정 시간이 지났는데 아직 확정 안 된 근무일지에 리마인더를 보낸다. */
    public void checkUnconfirmedWorkLogs() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : activeUserQueryClient.findActiveUserIds()) {
            CalendarDailySummary summary = calendarQueryClient.getDailySummary(userId, today, null, null);
            for (CalendarWorkBrief work : summary.getWorks()) {
                if (!WORK_LOG_PLANNED_STATUS.equals(work.getStatus())) {
                    continue;
                }
                LocalTime endTime = work.getEndTime();
                if (endTime == null) {
                    continue;
                }
                LocalDateTime reminderAt = LocalDateTime.of(today, endTime).plusMinutes(WORK_LOG_REMINDER_DELAY_MINUTES);
                if (now.isBefore(reminderAt)) {
                    continue;
                }
                notificationService.createNotification(
                        userId,
                        "worklog-unconfirmed-" + work.getWorkId(),
                        "WORK",
                        "근무일지를 확정해 주세요",
                        work.getJobName() + " 근무가 종료됐어요. 근무일지를 확정해 주세요."
                );
            }
        }
    }
}
