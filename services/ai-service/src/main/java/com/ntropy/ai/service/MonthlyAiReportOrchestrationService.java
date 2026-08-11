package com.ntropy.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.ai.client.fastapi.FastApiProductRecommendationClient;
import com.ntropy.ai.domain.AiReport;
import com.ntropy.ai.dto.fastapi.ProductRecommendationRequest;
import com.ntropy.ai.dto.fastapi.ProductRecommendationResponse;
import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.IncomeAnalysisQueryClient;
import com.ntropy.common.client.MonthlyExpenseQueryClient;
import com.ntropy.common.dto.account.MonthlyExpenseSummary;
import com.ntropy.common.dto.work.summary.JobFatigueSummary;
import com.ntropy.common.dto.work.summary.JobIncomeSummary;
import com.ntropy.common.dto.work.summary.MonthlyIncomeAnalysisSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 리포트 월간 배치를 담당하는 서비스입니다.
 *
 * <p>처리 순서:</p>
 *
 * <pre>
 * 활성 사용자 조회
 *   ↓
 * work-service 소득 조회
 *   ↓
 * account-service 소비 조회
 *   ↓
 * 리포트용 데이터 생성
 *   ↓
 * FastAPI 추천 요청
 *   ↓
 * AI_REPORT 저장
 * </pre>
 *
 * <p>
 * 이번 AI 리포트 배치에서는 diagnosis-service와
 * DIAGNOSIS_RESULT를 사용하지 않습니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyAiReportOrchestrationService {

    /**
     * 배치 대상 활성 사용자를 조회하는 Client입니다.
     */
    private final ActiveUserQueryClient activeUserQueryClient;

    /**
     * work-service에서 월별 소득 분석 결과를 조회하는 Client입니다.
     */
    private final IncomeAnalysisQueryClient incomeAnalysisQueryClient;

    /**
     * account-service에서 월별 소비 집계를 조회하는 Client입니다.
     */
    private final MonthlyExpenseQueryClient monthlyExpenseQueryClient;

    /**
     * FastAPI 금융상품 추천 Client입니다.
     */
    private final FastApiProductRecommendationClient recommendationClient;

    /**
     * AI_REPORT 저장 Service입니다.
     */
    private final AiReportService aiReportService;

    /**
     * Java 객체를 JSON 문자열로 변환합니다.
     */
    private final ObjectMapper objectMapper;


    /**
     * 지난달 기준으로 AI 리포트 배치를 실행합니다.
     *
     * <p>
     * 스케줄러가 매월 실행될 때 호출됩니다.
     * 예를 들어 2026년 8월 1일에 실행되면
     * 2026년 7월 데이터를 처리합니다.
     * </p>
     */
    public void runLastMonthBatch() {
        /*
         * 서버가 UTC 시간대에서 실행되더라도
         * 한국 기준 날짜를 사용하도록 합니다.
         */
        YearMonth targetYearMonth =
                YearMonth.now(
                        ZoneId.of("Asia/Seoul")
                ).minusMonths(1);

        runBatch(targetYearMonth);
    }

    /**
     * 월간 AI 리포트 배치를 실행합니다.
     *
     * @param targetYearMonth 리포트 대상 연월
     */

    public void runBatch(YearMonth targetYearMonth) {
        List<Long> userIds =
                activeUserQueryClient.findActiveUserIds();

        if (userIds == null || userIds.isEmpty()) {
            log.info(
                    "[AI 리포트 배치] 대상 사용자가 없습니다. yearMonth={}",
                    targetYearMonth
            );
            return;
        }

        /*
         * 사용자별로 처리합니다.
         *
         * 한 사용자의 데이터 오류가
         * 다른 사용자의 리포트 생성에 영향을 주지 않도록
         * 사용자 단위로 예외를 분리합니다.
         */
        for (Long userId : userIds) {
            try {
                processUserReport(userId, targetYearMonth);
            } catch (Exception e) {
                log.error(
                        "[AI 리포트 배치] 사용자 처리 실패. userId={}, yearMonth={}",
                        userId,
                        targetYearMonth,
                        e
                );
            }
        }
    }

    /**
     * 사용자 한 명의 AI 리포트를 생성합니다.
     */
    private void processUserReport(
            Long userId,
            YearMonth targetYearMonth
    ) {
        String yearMonth =
                targetYearMonth.toString();

        /*
         * work-service에서 해당 월의 소득 정보를 조회합니다.
         */
        MonthlyIncomeAnalysisSummary income =
                incomeAnalysisQueryClient.getMonthlyIncomeAnalysis(
                        userId,
                        targetYearMonth
                );

        /*
         * account-service에서 해당 월의 소비 정보를 조회합니다.
         */
        MonthlyExpenseSummary currentExpense =
                monthlyExpenseQueryClient.findMonthlyExpense(
                        userId,
                        yearMonth
                );

        /*
         * 전월 소비 정보를 조회합니다.
         */
        MonthlyExpenseSummary previousExpense =
                monthlyExpenseQueryClient.findMonthlyExpense(
                        userId,
                        targetYearMonth.minusMonths(1).toString()
                );

        /*
         * 소득이 없으면 0으로 처리합니다.
         */
        long totalIncome =
                income == null
                        || income.getTotalIncome() == null
                        ? 0L
                        : income.getTotalIncome();

        /*
         * 소비 정보가 없으면 총소비를 0으로 처리합니다.
         */
        long totalExpense =
                currentExpense == null
                        || currentExpense.getTotalExpense() == null
                        ? 0L
                        : currentExpense.getTotalExpense();

        /*
         * 가용자금 = 총소득 - 총소비
         */
        long availableFunds =
                totalIncome - totalExpense;

        /*
         * 잡별 소득과 근무시간을 jobId 기준으로 합칩니다.
         */
        List<Map<String, Object>> jobSummaries =
                buildJobSummaries(income);

        /*
         * FastAPI가 잡 관련 AI 인사이트를 만들 때 사용할 확장 입력입니다.
         *
         * jobSummaries는 프론트 표시용이고,
         * jobInsightInputs는 AI 판단용입니다.
         */
        List<Map<String, Object>> jobInsightInputs =
                buildJobInsightInputs(income);

        /*
         * 카테고리별 소비 Map을 프론트용 List로 변환합니다.
         */
        List<Map<String, Object>> topCategories =
                buildTopCategories(currentExpense);

        Long previousMonthExpense =
                previousExpense == null
                        || previousExpense.getTotalExpense() == null
                        ? null
                        : previousExpense.getTotalExpense();

        /*
         * 전월 대비 소비 증감률입니다.
         *
         * 예:
         * 전월 소비 100만 원
         * 이번 달 소비 80만 원
         *
         * 결과:
         * (80만 - 100만) / 100만 = -0.2
         */
        Double expenseChangeRate =
                calculateChangeRate(
                        previousMonthExpense,
                        totalExpense
                );

        /*
         * work-service에서 받은 소득 증감률도
         * 0~1 사이의 소수 형식으로 유지합니다.
         */
        Double incomeChangeRate =
                income == null
                        ? null
                        : roundRatio(
                        income.getIncomeChangeRate()
                );

        /*
         * FastAPI에는 카테고리 List를 JSON 문자열로 전달합니다.
         */
        String categoryExpensesJson =
                toJson(topCategories);

        /**
         * FastAPI 추천 요청 DTO를 생성합니다.
         *
         * <p>
         * 소득·소비의 핵심 금액뿐 아니라
         * 전월 대비 소득 변화와 잡별 소득 분석 정보도 함께 전달합니다.
         * </p>
         */
        ProductRecommendationRequest recommendationRequest =
                ProductRecommendationRequest.builder()
                        // 추천 대상 사용자
                        .userId(userId)

                        // 추천 대상 연월
                        .yearMonth(yearMonth)

                        // 해당 월 총소득
                        .totalIncome(totalIncome)

                        // 해당 월 총소비
                        .totalExpense(totalExpense)

                        // 총소득 - 총소비
                        .availableFunds(availableFunds)

                        // 카테고리별 소비 List를 JSON 문자열로 전달
                        .categoryExpenses(categoryExpensesJson)

                        // 전월 총소득
                        .previousMonthIncome(
                                income == null
                                        ? null
                                        : income.getPreviousMonthIncome()
                        )

                        // 전월 대비 소득 증감액
                        .incomeChangeAmount(
                                income == null
                                        ? null
                                        : income.getIncomeChangeAmount()
                        )

                        // 전월 대비 소득 증감률
                        // 0.25 = 25%가 아니라 비율 0.25로 전달
                        .incomeChangeRate(incomeChangeRate)

                        // 최근 소득 변동성
                        .incomeVolatility(
                                income == null
                                        ? null
                                        : income.getIncomeVolatility()
                        )

                        /*
                         * FastAPI에는 job_incomes와 fatigue_summaries를 따로 보내지 않고,
                         * jobId 기준으로 조합된 AI 판단용 입력만 전달합니다.
                         */
                        .jobInsightInputs(jobInsightInputs)

                        .build();
        /*
         * FastAPI 금융상품 추천을 요청합니다.
         */
        ProductRecommendationResponse recommendationResponse =
                recommendationClient.recommend(
                        recommendationRequest
                );

        /*
         * AI_REPORT.financial_summary_json에 저장할
         * 재무 요약 데이터를 생성합니다.
         */
        Map<String, Object> financialSummary =
                new LinkedHashMap<>();

        financialSummary.put(
                "totalIncome",
                totalIncome
        );

        financialSummary.put(
                "totalExpense",
                totalExpense
        );

        financialSummary.put(
                "availableFunds",
                availableFunds
        );

        /*
         * 값 예시: -0.08
         */
        financialSummary.put(
                "expenseChangeRate",
                expenseChangeRate
        );

        /*
         * 값 예시: 0.125
         */
        financialSummary.put(
                "incomeChangeRate",
                incomeChangeRate
        );

        /*
         * Map이 아닌 List 형태로 저장합니다.
         */
        financialSummary.put(
                "topCategories",
                topCategories
        );

        /*
         * 잡별 소득·근무시간도 List 형태로 저장합니다.
         */
        financialSummary.put(
                "jobSummaries",
                jobSummaries
        );

        String financialSummaryJson =
                toJson(financialSummary);

        /*
         * FastAPI 응답 전체를 recommendation_json에 저장합니다.
         */
        String recommendationJson =
                toJson(recommendationResponse);

        /*
         * 사용자·연월 기준으로 AI_REPORT를 upsert합니다.
         */
        aiReportService.upsert(
                AiReport.builder()
                        .userId(userId)
                        .yearMonth(yearMonth)
                        .financialSummaryJson(financialSummaryJson)
                        .recommendationJson(recommendationJson)
                        .build()
        );

        log.info(
                "[AI 리포트 배치] 사용자 처리 완료. userId={}, yearMonth={}",
                userId,
                yearMonth
        );
    }

    /**
     * 잡별 소득 목록과 잡별 근무시간 목록을
     * jobId 기준으로 하나의 List로 합칩니다.
     *
     * <p>결과 예시:</p>
     *
     * <pre>
     * [
     *   {
     *     "jobId": 1,
     *     "jobName": "배달",
     *     "incomeAmount": 1000000,
     *     "totalWorkMinutes": 2400,
     *     "incomeRatio": 0.25
     *   }
     * ]
     * </pre>
     */
    private List<Map<String, Object>> buildJobSummaries(
            MonthlyIncomeAnalysisSummary income
    ) {
        if (income == null) {
            return List.of();
        }

        Map<Long, Map<String, Object>> summaryByJob =
                new LinkedHashMap<>();

        /*
         * 잡별 소득 데이터를 등록합니다.
         */
        List<JobIncomeSummary> jobIncomes =
                income.getJobIncomes();

        if (jobIncomes != null) {
            for (JobIncomeSummary jobIncome : jobIncomes) {
                if (jobIncome == null
                        || jobIncome.getJobId() == null) {
                    continue;
                }

                Map<String, Object> summary =
                        summaryByJob.computeIfAbsent(
                                jobIncome.getJobId(),
                                key -> new LinkedHashMap<>()
                        );

                summary.put(
                        "jobId",
                        jobIncome.getJobId()
                );

                summary.put(
                        "jobName",
                        jobIncome.getJobName()
                );

                summary.put(
                        "incomeAmount",
                        jobIncome.getIncomeAmount()
                );

                /*
                 * incomeRatio는 이미 0~1 비율이므로
                 * 별도의 100 곱셈을 하지 않습니다.
                 *
                 * 예:
                 * 0.25 → 0.25
                 */
                summary.put(
                        "incomeRatio",
                        roundRatio(
                                jobIncome.getIncomeRatio()
                        )
                );
            }
        }

        /*
         * 잡별 근무시간을 추가합니다.
         */
        List<JobFatigueSummary> fatigueSummaries =
                income.getFatigueSummaries();

        if (fatigueSummaries != null) {
            for (JobFatigueSummary fatigue : fatigueSummaries) {
                if (fatigue == null
                        || fatigue.getJobId() == null) {
                    continue;
                }

                Map<String, Object> summary =
                        summaryByJob.computeIfAbsent(
                                fatigue.getJobId(),
                                key -> new LinkedHashMap<>()
                        );

                summary.put(
                        "jobId",
                        fatigue.getJobId()
                );

                /*
                 * 소득 목록에 잡 이름이 없는 경우를 대비합니다.
                 */
                summary.putIfAbsent(
                        "jobName",
                        fatigue.getJobName()
                );

                summary.put(
                        "totalWorkMinutes",
                        fatigue.getTotalWorkMinutes()
                );
            }
        }

        return new ArrayList<>(
                summaryByJob.values()
        );
    }

    /**
     * FastAPI가 잡 관련 AI 인사이트를 생성할 때 사용할 입력 데이터를 만듭니다.
     *
     * 프론트 표시용 jobSummaries보다 더 많은 정보를 포함합니다.
     *
     * 포함 정보:
     * - 잡별 소득
     * - 잡별 소득 비율
     * - 잡별 총 근무시간
     * - 잡별 근무일수
     * - 잡별 평균 피로도
     * - 잡별 최근 피로도
     */
    private List<Map<String, Object>> buildJobInsightInputs(
            MonthlyIncomeAnalysisSummary income
    ) {
        if (income == null) {
            return List.of();
        }

        Map<Long, Map<String, Object>> inputByJob =
                new LinkedHashMap<>();

        /*
         * 먼저 잡별 소득 정보를 등록합니다.
         */
        List<JobIncomeSummary> jobIncomes =
                income.getJobIncomes();

        if (jobIncomes != null) {
            for (JobIncomeSummary jobIncome : jobIncomes) {
                if (jobIncome == null
                        || jobIncome.getJobId() == null) {
                    continue;
                }

                Map<String, Object> input =
                        inputByJob.computeIfAbsent(
                                jobIncome.getJobId(),
                                key -> new LinkedHashMap<>()
                        );

                input.put(
                        "jobId",
                        jobIncome.getJobId()
                );

                input.put(
                        "jobName",
                        jobIncome.getJobName()
                );

                input.put(
                        "incomeAmount",
                        jobIncome.getIncomeAmount()
                );

                /*
                 * incomeRatio는 퍼센트가 아니라 0~1 사이 비율입니다.
                 *
                 * 예:
                 * 0.25 = 25%
                 */
                input.put(
                        "incomeRatio",
                        roundRatio(jobIncome.getIncomeRatio())
                );
            }
        }

        /*
         * 그 다음 잡별 근무시간과 피로도 정보를 추가합니다.
         */
        List<JobFatigueSummary> fatigueSummaries =
                income.getFatigueSummaries();

        if (fatigueSummaries != null) {
            for (JobFatigueSummary fatigue : fatigueSummaries) {
                if (fatigue == null
                        || fatigue.getJobId() == null) {
                    continue;
                }

                Map<String, Object> input =
                        inputByJob.computeIfAbsent(
                                fatigue.getJobId(),
                                key -> new LinkedHashMap<>()
                        );

                input.put(
                        "jobId",
                        fatigue.getJobId()
                );

                /*
                 * 소득 목록에 잡 이름이 없을 수도 있으므로,
                 * 피로도 목록의 잡 이름을 보조로 사용합니다.
                 */
                input.putIfAbsent(
                        "jobName",
                        fatigue.getJobName()
                );

                input.put(
                        "totalWorkMinutes",
                        fatigue.getTotalWorkMinutes()
                );

                input.put(
                        "workDays",
                        fatigue.getWorkDays()
                );

                input.put(
                        "averageFatigue",
                        fatigue.getAverageFatigue()
                );

                input.put(
                        "latestFatigue",
                        fatigue.getLatestFatigue()
                );
            }
        }

        return new ArrayList<>(
                inputByJob.values()
        );
    }

    /**
     * 카테고리별 소비 Map을 프론트용 List로 변환합니다.
     *
     * <p>결과 예시:</p>
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
    private List<Map<String, Object>> buildTopCategories(
            MonthlyExpenseSummary expense
    ) {
        if (expense == null
                || expense.getCategoryExpenses() == null) {
            return List.of();
        }

        long totalExpense =
                expense.getTotalExpense() == null
                        ? 0L
                        : expense.getTotalExpense();

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Map.Entry<String, Long> entry :
                expense.getCategoryExpenses().entrySet()) {

            String category =
                    entry.getKey();

            long amount =
                    entry.getValue() == null
                            ? 0L
                            : entry.getValue();

            Map<String, Object> categorySummary =
                    new LinkedHashMap<>();

            categorySummary.put(
                    "category",
                    category
            );

            categorySummary.put(
                    "displayName",
                    resolveCategoryDisplayName(category)
            );

            categorySummary.put(
                    "amount",
                    amount
            );

            /*
             * 카테고리 비율도 0~1 사이의 소수로 전달합니다.
             *
             * 예:
             * 8,000 / 58,000 = 0.1379
             */
            Double ratio =
                    totalExpense == 0
                            ? null
                            : roundRatio(
                            (double) amount / totalExpense
                    );

            categorySummary.put(
                    "ratio",
                    ratio
            );

            result.add(categorySummary);
        }

        return result;
    }

    /**
     * 전월 대비 증감률을 계산합니다.
     *
     * <p>결과는 퍼센트가 아니라 0~1 비율입니다.</p>
     *
     * <pre>
     * 전월 100만 원
     * 이번 달 80만 원
     *
     * (80만 - 100만) / 100만 = -0.2
     * </pre>
     */
    private Double calculateChangeRate(
            Long previousAmount,
            long currentAmount
    ) {
        if (previousAmount == null
                || previousAmount == 0L) {
            return null;
        }

        double rate =
                ((double) currentAmount - previousAmount)
                        / previousAmount;

        return roundRatio(rate);
    }

    /**
     * 비율을 소수 넷째 자리까지 반올림합니다.
     *
     * <p>예:</p>
     *
     * <pre>
     * 0.25   → 0.25
     * 0.1379 → 0.1379
     * -0.08  → -0.08
     * </pre>
     */
    private Double roundRatio(
            Double ratio
    ) {
        if (ratio == null) {
            return null;
        }

        return BigDecimal.valueOf(ratio)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 카테고리 코드를 화면 표시명으로 변환합니다.
     */
    private String resolveCategoryDisplayName(
            String category
    ) {
        if (category == null) {
            return "기타";
        }

        return switch (category) {
            case "FOOD" -> "식비";
            case "TRANSPORTATION" -> "교통비";
            case "HOUSING" -> "주거";
            case "COMMUNICATION" -> "통신";
            case "MEDICAL" -> "의료·건강";
            case "EDUCATION" -> "교육";
            case "SHOPPING" -> "쇼핑";
            case "LEISURE" -> "취미·여가";
            case "INSURANCE" -> "보험";
            case "FINANCE" -> "금융";
            case "ETC" -> "기타";
            default -> category;
        };
    }

    /**
     * 객체를 JSON 문자열로 변환합니다.
     */
    private String toJson(
            Object value
    ) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "AI 리포트 JSON 변환에 실패했습니다.",
                    e
            );
        }
    }
}