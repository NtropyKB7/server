package com.ntropy.notification.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.notification.domain.entity.Notification;
import com.ntropy.notification.exception.NotificationErrorCode;
import com.ntropy.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    @Transactional
    public Notification createNotification(Long userId, String eventId, String notificationType, String title, String body) {
        Optional<Notification> existing = notificationMapper.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("이미 처리된 이벤트라 알림 생성을 건너뜁니다: eventId={}", eventId);
            return existing.get();
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .eventId(eventId)
                .notificationType(notificationType)
                .title(title)
                .body(body)
                .build();

        notificationMapper.insertNotification(notification);
        return notification;
    }

    public List<Notification> getNotifications(Long userId, int page, int size) {
        return notificationMapper.findByUserId(userId, page * size, size);
    }

    public long countNotifications(Long userId) {
        return notificationMapper.countByUserId(userId);
    }

    public long countUnread(Long userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = getOwnedNotification(userId, notificationId);
        notificationMapper.markAsRead(notification.getNotificationId());
    }

    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification notification = getOwnedNotification(userId, notificationId);
        notificationMapper.softDelete(notification.getNotificationId());
    }

    private Notification getOwnedNotification(Long userId, Long notificationId) {
        return notificationMapper.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ServiceException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }
}
