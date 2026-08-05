package com.ntropy.user.dto.common;

import lombok.Builder;
import lombok.Getter;

// 나중에 common/src/main/java/com/ntropy/common/dto/user/ 로 이동할 파일
@Getter
@Builder
public class UserSummary {
    private Long userId;
    private String name;
    private String email;
}