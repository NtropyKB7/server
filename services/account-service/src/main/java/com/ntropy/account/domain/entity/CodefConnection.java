package com.ntropy.account.domain.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CODEF 커넥티드 아이디(connectedId)와 사용자를 매핑하는 도메인 객체.
 * 실제 은행/카드 로그인 정보는 CODEF가 보관하며, 여기서는 connectedId만 저장한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CodefConnection {

    private Long id;
    private Long userId;
    private String connectedId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
