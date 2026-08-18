package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.NotificationCommandClient;
import com.ntropy.common.domain.UserScope;
import com.ntropy.common.dto.notification.NotificationCreateCommand;
import com.ntropy.common.dto.notification.NotificationSummary;
import com.ntropy.work.config.WorkReminderBatchUserScopeProperties;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;

class WorkLogReminderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_ID = 100L;

    private final InMemoryWorkLogMapper workLogMapper = new InMemoryWorkLogMapper();
    private final StubActiveUserQueryClient activeUserQueryClient = new StubActiveUserQueryClient();
    private final StubNotificationCommandClient notificationCommandClient = new StubNotificationCommandClient();
    private final WorkLogReminderService service =
            new WorkLogReminderService(
                    activeUserQueryClient,
                    new WorkReminderBatchUserScopeProperties("REAL_ONLY"),
                    workLogMapper,
                    notificationCommandClient);

    @BeforeEach
    void setUp() {
        activeUserQueryClient.userIds = List.of(USER_ID);
    }

    @Test
    @DisplayName("오늘 근무일지가 하나도 없으면 미기록 리마인더를 보낸다")
    void checkNoWorkLogToday_noLogs_sendsNotification() {
        service.checkNoWorkLogToday();

        assertEquals(1, notificationCommandClient.created.size());
        assertEquals("WORK", notificationCommandClient.created.get(0).notificationType());
    }

    @Test
    @DisplayName("오늘 근무일지가 있으면 미기록 리마인더를 보내지 않는다")
    void checkNoWorkLogToday_hasLog_doesNotSendNotification() {
        workLogMapper.insert(workLog(LocalDate.now(), "CONFIRMED"));

        service.checkNoWorkLogToday();

        assertEquals(0, notificationCommandClient.created.size());
    }

    @Test
    @DisplayName("종료 후 1시간이 지난 PLANNED 근무일지가 있으면 건별로 미확정 리마인더를 보낸다")
    void checkUnconfirmedWorkLogs_pastDelay_sendsNotificationPerWorkLog() {
        WorkLog log = workLog(LocalDate.now(), "PLANNED", LocalTime.now().minusHours(1).minusMinutes(1));
        workLogMapper.insert(log);

        service.checkUnconfirmedWorkLogs();

        assertEquals(1, notificationCommandClient.created.size());
        assertEquals("worklog-unconfirmed-" + log.getLogId(), notificationCommandClient.created.get(0).eventId());
    }

    @Test
    @DisplayName("종료 후 1시간이 지나지 않았으면 미확정 리마인더를 보내지 않는다")
    void checkUnconfirmedWorkLogs_beforeDelayElapsed_doesNotSendNotification() {
        workLogMapper.insert(workLog(LocalDate.now(), "PLANNED", LocalTime.now().minusMinutes(30)));

        service.checkUnconfirmedWorkLogs();

        assertEquals(0, notificationCommandClient.created.size());
    }

    @Test
    @DisplayName("종료 후 1시간이 지났어도 CONFIRMED면 미확정 리마인더를 보내지 않는다")
    void checkUnconfirmedWorkLogs_confirmed_doesNotSendNotification() {
        workLogMapper.insert(workLog(LocalDate.now(), "CONFIRMED", LocalTime.now().minusHours(2)));

        service.checkUnconfirmedWorkLogs();

        assertEquals(0, notificationCommandClient.created.size());
    }

    @Test
    @DisplayName("종료 후 1시간이 지난 미확정 근무일지가 여러 건이면 각각 알림을 보낸다")
    void checkUnconfirmedWorkLogs_multiplePastDelay_sendsOnePerWorkLog() {
        workLogMapper.insert(workLog(LocalDate.now(), "PLANNED", LocalTime.now().minusHours(2)));
        workLogMapper.insert(workLog(LocalDate.now(), "PLANNED", LocalTime.now().minusHours(3)));

        service.checkUnconfirmedWorkLogs();

        assertEquals(2, notificationCommandClient.created.size());
    }

    @Test
    @DisplayName("오늘 근무일지가 아예 없으면 미확정 리마인더도 보내지 않는다")
    void checkUnconfirmedWorkLogs_noLogsAtAll_doesNotSendNotification() {
        service.checkUnconfirmedWorkLogs();

        assertEquals(0, notificationCommandClient.created.size());
    }

    @Test
    @DisplayName("설정된 batch.work-reminder.user-scope를 ActiveUserQueryClient에 그대로 전달한다")
    void checkNoWorkLogToday_passesConfiguredUserScopeToClient() {
        service.checkNoWorkLogToday();

        assertEquals(UserScope.REAL_ONLY, activeUserQueryClient.lastRequestedScope);
    }

    private static WorkLog workLog(LocalDate workDate, String status) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(JOB_ID)
                .workDate(workDate)
                .status(status)
                .build();
    }

    private static WorkLog workLog(LocalDate workDate, String status, LocalTime endTime) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(JOB_ID)
                .workDate(workDate)
                .status(status)
                .endTime(endTime)
                .build();
    }

    private static final class StubActiveUserQueryClient implements ActiveUserQueryClient {
        private List<Long> userIds = List.of();
        private UserScope lastRequestedScope;

        @Override
        public List<Long> findActiveUserIds(UserScope scope) {
            this.lastRequestedScope = scope;
            return userIds;
        }
    }

    private static final class StubNotificationCommandClient implements NotificationCommandClient {
        private final List<NotificationCreateCommand> created = new ArrayList<>();

        @Override
        public NotificationSummary create(NotificationCreateCommand command) {
            created.add(command);
            return null;
        }

        @Override
        public void markAsRead(Long userId, Long notificationId) {
        }

        @Override
        public void delete(Long userId, Long notificationId) {
        }
    }
}
