package com.ntropy.bff.controller.notification;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.notification.response.NotificationsResponse;
import com.ntropy.bff.dto.notification.response.UnreadCountResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.NotificationCommandClient;
import com.ntropy.common.client.NotificationQueryClient;

import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryClient notificationQueryClient;
    private final NotificationCommandClient notificationCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @GetMapping
    public ApiResponse<NotificationsResponse> getNotifications(@ApiParam(hidden = true) Authentication authentication,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(
                NotificationsResponse.from(notificationQueryClient.findNotifications(userId, page, size)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(new UnreadCountResponse(notificationQueryClient.countUnread(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(@ApiParam(hidden = true) Authentication authentication,
                                         @PathVariable Long notificationId) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        notificationCommandClient.markAsRead(userId, notificationId);
        return ApiResponse.success(200, "알림을 읽음 처리했습니다.", null);
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(@ApiParam(hidden = true) Authentication authentication,
                                     @PathVariable Long notificationId) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        notificationCommandClient.delete(userId, notificationId);
        return ApiResponse.success(200, "알림을 삭제했습니다.", null);
    }
}
