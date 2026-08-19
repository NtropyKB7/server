package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.NotificationCommandClient;
import com.ntropy.common.dto.notification.NotificationCreateCommand;
import com.ntropy.work.config.WorkReminderBatchUserScopeProperties;
import com.ntropy.work.domain.WorkLogStatus;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.mapper.WorkLogMapper;

import lombok.RequiredArgsConstructor;

/**
 * 근무일지 관련 리마인더를 위해 활성 유저를 폴링해서 알림을 만드는 서비스.
 * WorkLog는 work-service가 소유한 데이터이므로, 상태(미기록/미확정) 판단도 work-service가
 * 직접 하고 NotificationCommandClient로 알림 생성만 요청한다 - 원래 notification-service의
 * NotificationTriggerService에 있던 로직을 이 도메인으로 옮긴 것이다.
 * 스케줄러(WorkLogReminderScheduler)가 주기적으로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class WorkLogReminderService {

    private static final String STATUS_PLANNED = WorkLogStatus.PLANNED;
    private static final int UNCONFIRMED_REMINDER_DELAY_MINUTES = 60;

    private final ActiveUserQueryClient activeUserQueryClient;
    private final WorkReminderBatchUserScopeProperties userScopeProperties;
    private final WorkLogMapper workLogMapper;
    private final NotificationCommandClient notificationCommandClient;

    /** 매일 지정된 시각에 실행. 오늘 근무 기록이 없는 유저에게 리마인더를 보낸다. */
    public void checkNoWorkLogToday() {
        LocalDate today = LocalDate.now();
        for (Long userId : activeUserQueryClient.findActiveUserIds(userScopeProperties.getUserScope())) {
            List<WorkLog> logs = workLogMapper.findByUserIdAndWorkDate(userId, today);
            if (logs.isEmpty()) {
                notificationCommandClient.create(new NotificationCreateCommand(
                        userId,
                        "worklog-noWork-" + userId + "-" + today,
                        "WORK",
                        "오늘 근무 기록이 없어요",
                        "오늘 일하셨다면 근무일지를 작성해 주세요."
                ));
            }
        }
    }

    /**
     * 주기적으로 실행. 종료 후 UNCONFIRMED_REMINDER_DELAY_MINUTES가 지났는데 아직 확정 안 된
     * 근무일지가 있으면 건별로 리마인더를 보낸다. eventId를 근무일지 단위(logId)로 잡아서,
     * 폴링이 여러 번 겹쳐도 같은 근무일지에는 한 번만 알림이 간다.
     */
    public void checkUnconfirmedWorkLogs() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : activeUserQueryClient.findActiveUserIds(userScopeProperties.getUserScope())) {
            for (WorkLog workLog : workLogMapper.findByUserIdAndWorkDate(userId, today)) {
                if (!STATUS_PLANNED.equals(workLog.getStatus())) {
                    continue;
                }
                LocalTime endTime = workLog.getEndTime();
                if (endTime == null) {
                    continue;
                }
                LocalDateTime reminderAt = LocalDateTime.of(today, endTime)
                        .plusMinutes(UNCONFIRMED_REMINDER_DELAY_MINUTES);
                if (now.isBefore(reminderAt)) {
                    continue;
                }
                notificationCommandClient.create(new NotificationCreateCommand(
                        userId,
                        "worklog-unconfirmed-" + workLog.getLogId(),
                        "WORK",
                        "확정되지 않은 근무가 있어요",
                        "종료되었는데 확정되지 않은 근무가 있어요. 확정해 주세요."
                ));
            }
        }
    }
}
