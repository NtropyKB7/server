package com.ntropy.bff.controller.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.dashboard.response.DashboardResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.dto.user.UserSummary;
import com.ntropy.common.exception.ServiceException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

/**
 * 홈 대시보드 조합 엔드포인트.
 * 지금은 user-service의 UserQueryClient만 있어서 인사말만 채운다.
 * 피로도(FatigueQueryClient), 목표수입/실질시급(diagnosis-service client)이
 * 붙는 대로 이 컨트롤러에 조회 계약을 주입받아 응답에 필드를 추가한다.
 */
@Api(tags = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserQueryClient userQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("홈 대시보드 조회")
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        UserSummary summary = userQueryClient.getUserSummary(userId);
        if (summary == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND);
        }
        return ApiResponse.success(DashboardResponse.from(summary));
    }
}
