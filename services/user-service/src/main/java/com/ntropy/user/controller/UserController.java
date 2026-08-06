package com.ntropy.user.controller;

import com.ntropy.user.model.User;
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

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserInfo(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        Long currentUserId = Long.valueOf(userDetails.getUsername());
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        if (!currentUserId.equals(userId) && !"ROLE_ADMIN".equals(role)) {
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
            throw new AccessDeniedException("자신의 정보만 수정할 수 있습니다.");
        }

        user.setUserId(userId);
        userService.updateUser(user);

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
            throw new AccessDeniedException("자신 또는 관리자만 사용자를 삭제할 수 있습니다.");
        }

        userService.deleteUser(userId, request);
        return ResponseEntity.noContent().build();
    }
}