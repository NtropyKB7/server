package com.ntropy.user.dto;

import com.ntropy.user.model.User;
import lombok.Getter;

@Getter
public class UserResponseDto {

    private Long userId;
    private String email;
    private String name;
    private String provider;
    private Boolean onboardingCompleted;

    // User 모델을 DTO로 변환하는 생성자
    public UserResponseDto(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.provider = user.getProvider();
        this.onboardingCompleted = user.getOnboardingCompleted();
    }
}