package com.ntropy.bff.controller.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.dashboard.response.DashboardHoursResponse;
import com.ntropy.bff.dto.dashboard.response.DashboardIncomeResponse;
import com.ntropy.bff.dto.dashboard.response.DashboardResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.CalendarQueryClient;
import com.ntropy.common.client.IncomeAnalysisQueryClient;
import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.dto.user.UserSummary;
import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarMonthlyHours;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;
import com.ntropy.common.dto.work.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.common.exception.ServiceException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

/**
 * 홈 대시보드 조합 엔드포인트.
 * realWage(실질 시급)는 계산 공식이 아직 없어서 뺐다.
 * jobRecommendations(ROI 추천)는 장아연님 담당 별도 엔드포인트라 여기서 안 다룬다.
 */
@Api(tags = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserQueryClient userQueryClient;
    private final CalendarQueryClient calendarQueryClient;
    private final IncomeAnalysisQueryClient incomeAnalysisQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("홈 대시보드 조회")
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);

        UserSummary user = userQueryClient.getUserSummary(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND);
        }

        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        CalendarMonthlySummary monthlySummary = calendarQueryClient.getMonthlySummary(
                userId, thisMonth.getYear(), thisMonth.getMonthValue(), null, null);
        CalendarMonthlyHours hours = monthlySummary.getSummary();

        MonthlyIncomeAnalysisSummary incomeAnalysis =
                incomeAnalysisQueryClient.getMonthlyIncomeAnalysis(userId, thisMonth);

        CalendarDailySummary dailySummary = calendarQueryClient.getDailySummary(userId, today, null, null);
        Integer fatigueScore = dailySummary.getFatigue() == null ? null : dailySummary.getFatigue().getScore();

        DashboardResponse response = new DashboardResponse(
                user.name(),
                new DashboardHoursResponse(hours.getActualHours(), hours.getPlannedHours()),
                new DashboardIncomeResponse(incomeAnalysis.getTotalIncome(), hours.getTargetAmount()),
                fatigueScore
        );
        return ApiResponse.success(response);
    }
}
