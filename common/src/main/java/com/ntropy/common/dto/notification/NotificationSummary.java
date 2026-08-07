package com.ntropy.common.dto.notification;

import java.time.LocalDateTime;

/** 외부 모듈에 노출하는 알림 정보. */
public record NotificationSummary(
        Long notificationId,
        String eventId,
        String notificationType,
        String title,
        String body,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
