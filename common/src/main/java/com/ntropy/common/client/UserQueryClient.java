package com.ntropy.common.client;

import com.ntropy.common.dto.user.UserSummary;

/** 회원 도메인이 제공할 회원 조회 계약. */
public interface UserQueryClient {

    UserSummary getUserSummary(Long userId);
}
