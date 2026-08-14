package com.ntropy.bff.controller.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.dashboard.response.DashboardHoursResponse;
import com.ntropy.bff.dto.dashboard.response.DashboardIncomeResponse;
import com.ntropy.bff.dto.dashboard.response.DashboardResponse;
import com.ntropy.bff.dto.dashboard.response.JobRecommendationsResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.CalendarQueryClient;
import com.ntropy.common.client.IncomeAnalysisQueryClient;
import com.ntropy.common.client.RecommendedWorkHoursQueryClient;
import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.dto.user.UserSummary;
import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarMonthlyHours;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;
import com.ntropy.common.dto.work.summary.JobFatigueSummary;
import com.ntropy.common.dto.work.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.common.exception.ServiceException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

/**
 * 홈 대시보드 조합 엔드포인트.
 * realWage(실질 시급)는 계산 공식이 아직 없어서 뺐다.
 * 잡별 추천 근무시간(ROI 계산)은 장아연님이 만든 계약(RecommendedWorkHoursQueryClient)을
 * 가져다 /api/dashboard/recommendation-hours로 노출만 한다 — 계산 로직은 work-service에 있다.
 */
@Api(tags = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserQueryClient userQueryClient;
    private final CalendarQueryClient calendarQueryClient;
    private final IncomeAnalysisQueryClient incomeAnalysisQueryClient;
    private final RecommendedWorkHoursQueryClient recommendedWorkHoursQueryClient;
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

    @ApiOperation("잡별 추천 근무시간 조회")
    @GetMapping("/recommendation-hours")
    public ApiResponse<JobRecommendationsResponse> getRecommendationHours(
            @ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        YearMonth thisMonth = YearMonth.now();

        MonthlyIncomeAnalysisSummary incomeAnalysis =
                incomeAnalysisQueryClient.getMonthlyIncomeAnalysis(userId, thisMonth);
        Map<Long, Long> currentHoursByJobId = incomeAnalysis.getFatigueSummaries().stream()
                .collect(Collectors.toMap(JobFatigueSummary::getJobId, s -> s.getTotalWorkMinutes() / 60));

        JobRecommendationsResponse response = JobRecommendationsResponse.from(
                recommendedWorkHoursQueryClient.getCurrentMonthRecommendedWorkHours(userId),
                currentHoursByJobId);
        return ApiResponse.success(response);
    }
}
