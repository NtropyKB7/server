package com.ntropy.common.client;

/** 회원 도메인이 제공할 생년월일 조회 계약. CODEF 조건부 필드에만 사용한다. */
public interface UserBirthDateQueryClient {

    String findBirthDate(Long userId);
}
