package com.ntropy.notification.client;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.NotificationCommandClient;
import com.ntropy.common.dto.notification.NotificationCreateCommand;
import com.ntropy.common.dto.notification.NotificationSummary;
import com.ntropy.notification.domain.entity.Notification;
import com.ntropy.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** notification-service가 구현하는 알림 생성/읽음처리/삭제 계약. */
@Component
@RequiredArgsConstructor
public class LocalNotificationCommandClient implements NotificationCommandClient {

    private final NotificationService notificationService;

    @Override
    public NotificationSummary create(NotificationCreateCommand command) {
        Notification notification = notificationService.createNotification(
                command.userId(),
                command.eventId(),
                command.notificationType(),
                command.title(),
                command.body()
        );

        return new NotificationSummary(
                notification.getNotificationId(),
                notification.getEventId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    @Override
    public void markAsRead(Long userId, Long notificationId) {
        notificationService.markAsRead(userId, notificationId);
    }

    @Override
    public void delete(Long userId, Long notificationId) {
        notificationService.delete(userId, notificationId);
    }
}
