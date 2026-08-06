package com.ntropy.user.controller;

import com.ntropy.user.model.User;
import com.ntropy.user.service.AccessLogService;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccessLogService accessLogService;

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserInfo(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        Long currentUserId = Long.valueOf(userDetails.getUsername());
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        if (!currentUserId.equals(userId) && !"ROLE_ADMIN".equals(role)) {
            accessLogService.logActivity(request, currentUserId, "VIEW_USER_INFO_FAILURE", "다른 사용자 정보 조회 시도: " + userId, false);
            throw new AccessDeniedException("자신의 정보만 조회할 수 있습니다.");
        }

        User user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(
            @PathVariable Long userId,
            @RequestBody User user,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        Long currentUserId = Long.valueOf(userDetails.getUsername());
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        if (!currentUserId.equals(userId) && !"ROLE_ADMIN".equals(role)) {
            accessLogService.logActivity(request, currentUserId, "UPDATE_USER_INFO_FAILURE", "다른 사용자 정보 수정 시도: " + userId, false);
            throw new AccessDeniedException("자신의 정보만 수정할 수 있습니다.");
        }

        user.setUserId(userId);
        userService.updateUser(user);
        accessLogService.logActivity(request, currentUserId, "UPDATE_USER_INFO_SUCCESS", "사용자 정보 수정 성공: " + userId, true);

        User updatedUser = userService.getUserById(userId);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        Long currentUserId = Long.valueOf(userDetails.getUsername());
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        if (!currentUserId.equals(userId) && !"ROLE_ADMIN".equals(role)) {
            accessLogService.logActivity(request, currentUserId, "DELETE_USER_FAILURE", "다른 사용자 삭제 시도: " + userId, false);
            throw new AccessDeniedException("자신 또는 관리자만 사용자를 삭제할 수 있습니다.");
        }

        userService.deleteUser(userId, request); // deleteUser는 이미 로그 기록 로직이 있음
        return ResponseEntity.noContent().build();
    }
}