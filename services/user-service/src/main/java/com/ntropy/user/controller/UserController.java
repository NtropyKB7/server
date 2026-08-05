package com.ntropy.user.controller;

import com.ntropy.user.model.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserInfo(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long currentUserId = Long.valueOf(userDetails.getUsername());
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        // 데이터 소유권 검증
        // 현재 로그인한 사용자가 요청한 userId와 다르면서, 관리자(ADMIN) 권한도 없는 경우
        if (!currentUserId.equals(userId) && !"ROLE_ADMIN".equals(role)) {
            throw new AccessDeniedException("자신의 정보만 조회할 수 있습니다.");
        }

        User user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }
}