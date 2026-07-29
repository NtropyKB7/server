package com.ntropy.user.controller;

import com.ntropy.user.model.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * 회원 프로필 조회
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        log.info("==========> 회원 정보 조회 요청: userId={}", id);

        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    /**
     * 회원 정보 수정
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        log.info("==========> 회원 정보 수정 요청: userId={}", id);

        user.setUserId(id); // User 모델의 PK 필드명인 userId로 설정
        userService.updateUser(user);

        User updatedUser = userService.getUserById(id);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * 회원 탈퇴
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("==========> 회원 탈퇴 요청: userId={}", id);

        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}