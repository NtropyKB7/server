package com.ntropy.user.client.common;

import com.ntropy.user.dto.common.UserSummary;

// 나중에 common/src/main/java/com/ntropy/common/client/ 로 이동할 파일
public interface UserQueryClient {
    UserSummary getUserSummary(Long userId);
}