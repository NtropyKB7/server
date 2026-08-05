package com.ntropy.user.client;

import com.ntropy.user.client.common.UserQueryClient;
import com.ntropy.user.dto.common.UserSummary;
import com.ntropy.user.model.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service // bff-service가 주입받을 수 있도록 스프링 빈으로 등록
@RequiredArgsConstructor
public class LocalUserQueryClient implements UserQueryClient {

    private final UserService userService;

    @Override
    public UserSummary getUserSummary(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            // 또는 적절한 예외 처리
            return null;
        }

        // User 모델을 UserSummary DTO로 변환하여 반환
        return UserSummary.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}