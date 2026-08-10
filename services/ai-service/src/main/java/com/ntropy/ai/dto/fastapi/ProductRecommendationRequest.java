package com.ntropy.ai.dto.fastapi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ntropy.common.dto.work.summary.JobFatigueSummary;
import com.ntropy.common.dto.work.summary.JobIncomeSummary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI-service가 FastAPI 추천 API에 전달하는 요청 DTO입니다.
 *
 * 원천 거래 전체가 아니라,
 * 월별 소득·소비 집계 결과와
 * 추천에 필요한 소득 분석 정보만 전달합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecommendationRequest {

    /**
     * 추천 대상 사용자 ID입니다.
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * 추천 대상 연월입니다.
     */
    @JsonProperty("year_month")
    private String yearMonth;

    /**
     * 해당 월의 확정 총소득입니다.
     */
    @JsonProperty("total_income")
    private Long totalIncome;


    /**
     * 해당 월의 총소비입니다.
     */
    @JsonProperty("total_expense")
    private Long totalExpense;

    /**
     * 총소득에서 총소비를 뺀 가용금액입니다.
     */
    @JsonProperty("available_funds")
    private Long availableFunds;

    /**
     * 카테고리별 소비 금액 목록입니다.
     *
     * <p>
     * Java에서는 JSON 문자열로 변환해 전달합니다.
     * 실제 JSON 형태는 Map이 아니라 List입니다.
     * </p>
     *
     * <pre>
     * [
     *   {
     *     "category": "FOOD",
     *     "displayName": "식비",
     *     "amount": 8000,
     *     "ratio": 0.1379
     *   }
     * ]
     * </pre>
     */
    @JsonProperty("category_expenses")
    private String categoryExpenses;

    /**
     * 전월 총소득입니다.
     */
    @JsonProperty("previous_month_income")
    private Long previousMonthIncome;

    /**
     * 전월 대비 소득 증감액입니다.
     */
    @JsonProperty("income_change_amount")
    private Long incomeChangeAmount;

    /**
     * 전월 대비 소득 증감률입니다.
     */
    @JsonProperty("income_change_rate")
    private Double incomeChangeRate;

    /**
     * 최근 소득 변동성입니다.
     */
    @JsonProperty("income_volatility")
    private Double incomeVolatility;

    /**
     * 잡별 소득 목록입니다.
     */
    @JsonProperty("job_incomes")
    private List<JobIncomeSummary> jobIncomes;

    /**
     * 잡별 근무시간·피로도 요약 목록입니다.
     */
    @JsonProperty("fatigue_summaries")
    private List<JobFatigueSummary> fatigueSummaries;
}