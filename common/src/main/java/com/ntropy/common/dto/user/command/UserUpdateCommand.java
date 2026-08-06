package com.ntropy.common.dto.user.command;

/** 회원 정보 수정 명령. null인 필드는 변경하지 않는다. */
public record UserUpdateCommand(
        String name,
        String email,
        Boolean alarmAgree,
        Boolean locationAgree
) {
}
