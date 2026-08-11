package com.ntropy.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.common.dto.defense.summary.DefenseModeSummary;
import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarWorkBrief;

class NotificationTriggerServiceTest {

    private final InMemoryNotificationMapper mapper = new InMemoryNotificationMapper();
    private final StubUserQueryClient userQueryClient = new StubUserQueryClient();
    private final NotificationService notificationService = new NotificationService(mapper, userQueryClient);
    private final StubActiveUserQueryClient activeUserQueryClient = new StubActiveUserQueryClient(List.of(1L));
    private final StubDefenseModeQueryClient defenseModeQueryClient = new StubDefenseModeQueryClient();
    private final StubCalendarQueryClient calendarQueryClient = new StubCalendarQueryClient();
    private final NotificationTriggerService triggerService = new NotificationTriggerService(
            activeUserQueryClient, defenseModeQueryClient, calendarQueryClient, notificationService);

    @Test
    void createsEntryNotificationWhenUserIsInActiveDefenseMode() {
        userQueryClient.withAlarmAgree(1L, true);
        defenseModeQueryClient.withActive(1L, defenseSummary(100L, "ACTIVE", 10));

        triggerService.checkDefenseModeTriggers();

        assertEquals(1, mapper.rows.size());
        assertEquals("defense-enter-100", mapper.rows.get(0).getEventId());
    }

    @Test
    void createsDdayWarningWhenDdayIsLowEnough() {
        userQueryClient.withAlarmAgree(1L, true);
        defenseModeQueryClient.withActive(1L, defenseSummary(100L, "ACTIVE", 2));

        triggerService.checkDefenseModeTriggers();

        assertEquals(2, mapper.rows.size());
        assertTrue(mapper.rows.stream().anyMatch(n -> n.getEventId().startsWith("defense-dday-warning-100")));
    }

    @Test
    void skipsUserWithoutActiveDefenseMode() {
        userQueryClient.withAlarmAgree(1L, true);
        // defenseModeQueryClient에 활성 상태를 등록하지 않음 -> getCurrent가 예외를 던짐

        triggerService.checkDefenseModeTriggers();

        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void createsReminderWhenNoWorkLogToday() {
        userQueryClient.withAlarmAgree(1L, true);
        calendarQueryClient.withDailySummary(1L, LocalDate.now(),
                new CalendarDailySummary(LocalDate.now(), "월", List.of(), null, null));

        triggerService.checkNoWorkLogToday();

        assertEquals(1, mapper.rows.size());
        assertTrue(mapper.rows.get(0).getEventId().startsWith("worklog-noWork-1-"));
    }

    @Test
    void skipsReminderWhenWorkLogExistsToday() {
        userQueryClient.withAlarmAgree(1L, true);
        CalendarWorkBrief work = new CalendarWorkBrief(1L, 1L, "배달", LocalTime.of(9, 0), LocalTime.of(12, 0), "CONFIRMED");
        calendarQueryClient.withDailySummary(1L, LocalDate.now(),
                new CalendarDailySummary(LocalDate.now(), "월", List.of(work), null, null));

        triggerService.checkNoWorkLogToday();

        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void createsReminderForUnconfirmedWorkLogPastDelay() {
        userQueryClient.withAlarmAgree(1L, true);
        LocalTime endTime = LocalDateTime.now().minusMinutes(31).toLocalTime();
        CalendarWorkBrief work = new CalendarWorkBrief(7L, 1L, "배달", LocalTime.of(9, 0), endTime, "PLANNED");
        calendarQueryClient.withDailySummary(1L, LocalDate.now(),
                new CalendarDailySummary(LocalDate.now(), "월", List.of(work), null, null));

        triggerService.checkUnconfirmedWorkLogs();

        assertEquals(1, mapper.rows.size());
        assertEquals("worklog-unconfirmed-7", mapper.rows.get(0).getEventId());
    }

    @Test
    void skipsUnconfirmedReminderBeforeDelayElapsed() {
        userQueryClient.withAlarmAgree(1L, true);
        LocalTime endTime = LocalDateTime.now().minusMinutes(5).toLocalTime();
        CalendarWorkBrief work = new CalendarWorkBrief(7L, 1L, "배달", LocalTime.of(9, 0), endTime, "PLANNED");
        calendarQueryClient.withDailySummary(1L, LocalDate.now(),
                new CalendarDailySummary(LocalDate.now(), "월", List.of(work), null, null));

        triggerService.checkUnconfirmedWorkLogs();

        assertTrue(mapper.rows.isEmpty());
    }

    private DefenseModeSummary defenseSummary(Long defenseId, String status, Integer dDay) {
        return new DefenseModeSummary(
                defenseId, 1L, "ACCIDENT", "사고",
                null, null, null,
                null, null, null, null, null,
                dDay, null, status, null,
                List.of(), null, null, null);
    }
}
