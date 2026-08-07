package com.ntropy.notification.service;

/** 다른 패키지의 테스트(client 계층 등)에서 NotificationService를 조립할 때 쓰는 헬퍼. */
public class NotificationServiceTestSupport {

    private final NotificationService service;

    private NotificationServiceTestSupport(NotificationService service) {
        this.service = service;
    }

    public static NotificationServiceTestSupport withAlarmAgree(Long userId, boolean alarmAgree) {
        InMemoryNotificationMapper mapper = new InMemoryNotificationMapper();
        StubUserQueryClient userQueryClient = new StubUserQueryClient().withAlarmAgree(userId, alarmAgree);
        return new NotificationServiceTestSupport(new NotificationService(mapper, userQueryClient));
    }

    public NotificationService service() {
        return service;
    }
}
