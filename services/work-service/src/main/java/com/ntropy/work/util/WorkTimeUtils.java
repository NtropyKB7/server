package com.ntropy.work.util;

import java.time.Duration;
import java.time.LocalTime;

/**
 * WORK_LOG 시간 계산 공용 유틸. 자정을 넘기는 근무(예: 23:30~02:00)는
 * 24시간을 더해 계산한다 (시작 시각이 속한 날짜 기준).
 */
public final class WorkTimeUtils {

    private WorkTimeUtils() {
    }

    /**
     * startTime/endTime이 null이거나 서로 같으면 0을 반환한다 (예외를 던지지 않음 — 유효성
     * 검증이 필요한 호출부는 durationMinutes 호출 전에 직접 검증할 것).
     */
    public static long durationMinutes(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || startTime.equals(endTime)) {
            return 0;
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return minutes;
    }

    public static int durationHours(LocalTime startTime, LocalTime endTime) {
        return (int) (durationMinutes(startTime, endTime) / 60);
    }
}
