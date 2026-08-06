package com.ntropy.bff.controller.user;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.user.request.UserUpdateRequest;
import com.ntropy.bff.dto.user.response.UserResponse;
import com.ntropy.common.client.UserCommandClient;
import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.dto.user.UserSummary;
import com.ntropy.common.exception.ServiceException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

@Api(tags = "회원")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryClient userQueryClient;
    private final UserCommandClient userCommandClient;

    @ApiOperation("회원 정보 조회")
    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUserInfo(
            @PathVariable Long userId // TODO: AUTH 연동 후 인증 사용자 ID 사용 (현재 본인 확인 미적용)
    ) {
        UserSummary summary = userQueryClient.getUserSummary(userId);
        if (summary == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND);
        }
        return ApiResponse.success(UserResponse.from(summary));
    }

    @ApiOperation("회원 정보 수정")
    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long userId, // TODO: AUTH 연동 후 인증 사용자 ID 사용 (현재 본인 확인 미적용)
            @RequestBody UserUpdateRequest request
    ) {
        if (request == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST);
        }
        userCommandClient.updateUser(userId, request.toCommand());
        return ApiResponse.success(UserResponse.from(userQueryClient.getUserSummary(userId)));
    }

    @ApiOperation("회원 탈퇴")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(
            @PathVariable Long userId // TODO: AUTH 연동 후 인증 사용자 ID 사용 (현재 본인 확인 미적용)
    ) {
        userCommandClient.deleteUser(userId);
        return ApiResponse.success(200, "회원 탈퇴가 완료되었습니다.", null);
    }
}
