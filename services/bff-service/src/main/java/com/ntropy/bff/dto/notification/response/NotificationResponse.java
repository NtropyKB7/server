package com.ntropy.bff.dto.notification.response;

import java.time.LocalDateTime;

import com.ntropy.common.dto.notification.NotificationSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationResponse {

    private Long notificationId;
    private String notificationType;
    private String title;
    private String body;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationResponse from(NotificationSummary summary) {
        return new NotificationResponse(
                summary.notificationId(),
                summary.notificationType(),
                summary.title(),
                summary.body(),
                summary.readAt(),
                summary.createdAt()
        );
    }
}
