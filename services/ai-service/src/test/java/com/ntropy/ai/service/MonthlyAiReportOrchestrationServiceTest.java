package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.ai.client.fastapi.FastApiProductRecommendationClient;
import com.ntropy.ai.config.AiReportBatchUserScopeProperties;
import com.ntropy.ai.domain.AiReport;
import com.ntropy.ai.dto.fastapi.ProductRecommendationRequest;
import com.ntropy.ai.dto.fastapi.ProductRecommendationResponse;
import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.IncomeAnalysisQueryClient;
import com.ntropy.common.client.MonthlyExpenseQueryClient;
import com.ntropy.common.domain.UserScope;
import com.ntropy.common.dto.account.MonthlyExpenseSummary;
import com.ntropy.common.dto.work.summary.MonthlyIncomeAnalysisSummary;

class MonthlyAiReportOrchestrationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("빈 사용자 목록이면 예외 없이 배치가 종료된다")
    void runBatch_whenTargetUserListIsEmpty_completesWithoutException() {
        MonthlyAiReportOrchestrationService orchestrationService =
                createService(
                        scope -> List.of(),
                        (userId, yearMonth) -> null,
                        (userId, yearMonth) -> null,
                        new CapturingRecommendationClient(),
                        new CapturingAiReportService()
                );

        assertDoesNotThrow(
                () -> orchestrationService.runBatch(
                        YearMonth.of(2026, 7)
                )
        );
    }

    @Test
    @DisplayName("소득분석은 사용자별 개별 호출이 아니라 벌크 조회를 사용한다")
    void runBatch_usesBulkIncomeAnalysisInsteadOfPerUserCalls() {
        RecordingIncomeAnalysisQueryClient income =
                new RecordingIncomeAnalysisQueryClient();

        MonthlyAiReportOrchestrationService service = createService(
                scope -> List.of(1L, 2L, 3L),
                income,
                (userId, yearMonth) -> null,
                new CapturingRecommendationClient(),
                new CapturingAiReportService()
        );

        service.runBatch(YearMonth.of(2026, 7));

        assertEquals(0, income.singleCallCount,
                "getMonthlyIncomeAnalysis(단건)는 더 이상 호출되면 안 됩니다");
        assertEquals(1, income.bulkCallCount,
                "getMonthlyIncomeAnalysisBulk는 사용자 수와 무관하게 1번만 호출돼야 합니다");
        assertEquals(List.of(1L, 2L, 3L), income.lastBulkUserIds);
    }

    @Test
    @DisplayName("카테고리가 4개 이상이면 상위 3개와 합산된 기타를 반환한다")
    void buildTopCategories_whenFourOrMoreCategories_returnsTopThreeAndAggregatedOther() {
        MonthlyAiReportOrchestrationService service = createUnitService();
        MonthlyExpenseSummary expense = expenseSummary(
                2_680_000L,
                linkedCategories(
                        entry("FOOD", 760_000L),
                        entry("SHOPPING", 540_000L),
                        entry("TRANSPORTATION", 310_000L),
                        entry("COMMUNICATION", 120_000L),
                        entry("ETC", 950_000L)
                )
        );

        List<Map<String, Object>> result =
                service.buildTopCategories(expense);

        assertEquals(4, result.size());
        assertEquals("FOOD", result.get(0).get("category"));
        assertEquals("SHOPPING", result.get(1).get("category"));
        assertEquals("TRANSPORTATION", result.get(2).get("category"));
        assertEquals("AGGREGATED_OTHER", result.get(3).get("category"));
        assertEquals("기타", result.get(3).get("displayName"));
        assertEquals(1_070_000L, result.get(3).get("amount"));
        assertEquals(0.3992, (Double) result.get(3).get("ratio"), 0.00001);
        assertFalse(hasCategory(result, "ETC"));
        assertEquals(2_680_000L, sumAmounts(result));
        assertEquals(1.0, sumRatios(result), 0.00001);
    }

    @Test
    @DisplayName("구체적인 카테고리가 3개이고 원천 ETC가 없으면 기타를 만들지 않는다")
    void buildTopCategories_whenThreeConcreteCategoriesAndNoEtc_doesNotCreateAggregatedOther() {
        MonthlyAiReportOrchestrationService service = createUnitService();
        MonthlyExpenseSummary expense = expenseSummary(
                600_000L,
                linkedCategories(
                        entry("FOOD", 300_000L),
                        entry("SHOPPING", 200_000L),
                        entry("HOUSING", 100_000L)
                )
        );

        List<Map<String, Object>> result =
                service.buildTopCategories(expense);

        assertEquals(3, result.size());
        assertFalse(hasCategory(result, "AGGREGATED_OTHER"));
        assertEquals(1.0, sumRatios(result), 0.00001);
    }

    @Test
    @DisplayName("구체적인 카테고리가 3개이고 원천 ETC가 있으면 기타를 만든다")
    void buildTopCategories_whenThreeConcreteCategoriesAndSourceEtc_createsAggregatedOther() {
        MonthlyAiReportOrchestrationService service = createUnitService();
        MonthlyExpenseSummary expense = expenseSummary(
                700_000L,
                linkedCategories(
                        entry("FOOD", 300_000L),
                        entry("SHOPPING", 200_000L),
                        entry("HOUSING", 100_000L),
                        entry("ETC", 100_000L)
                )
        );

        List<Map<String, Object>> result =
                service.buildTopCategories(expense);

        assertEquals(4, result.size());
        assertEquals("AGGREGATED_OTHER", result.get(3).get("category"));
        assertEquals(100_000L, result.get(3).get("amount"));
        assertFalse(hasCategory(result, "ETC"));
    }

    @Test
    @DisplayName("금액이 null/0/음수인 카테고리는 제외한다")
    void buildTopCategories_excludesNullZeroAndNegativeAmounts() {
        MonthlyAiReportOrchestrationService service = createUnitService();
        MonthlyExpenseSummary expense = expenseSummary(
                300_000L,
                linkedCategories(
                        entry("FOOD", 300_000L),
                        entry("SHOPPING", 0L),
                        entry("MEDICAL", -1L),
                        entry("ETC", null)
                )
        );

        List<Map<String, Object>> result =
                service.buildTopCategories(expense);

        assertEquals(1, result.size());
        assertEquals("FOOD", result.get(0).get("category"));
        assertEquals(1.0, (Double) result.get(0).get("ratio"), 0.00001);
    }

    @Test
    @DisplayName("금액이 동률이면 카테고리 코드 오름차순으로 정렬한다")
    void buildTopCategories_whenAmountsAreEqual_usesCategoryCodeAsTieBreaker() {
        MonthlyAiReportOrchestrationService service = createUnitService();
        MonthlyExpenseSummary expense = expenseSummary(
                400_000L,
                linkedCategories(
                        entry("SHOPPING", 100_000L),
                        entry("HOUSING", 100_000L),
                        entry("FOOD", 100_000L),
                        entry("MEDICAL", 100_000L)
                )
        );

        List<Map<String, Object>> result =
                service.buildTopCategories(expense);

        assertEquals("FOOD", result.get(0).get("category"));
        assertEquals("HOUSING", result.get(1).get("category"));
        assertEquals("MEDICAL", result.get(2).get("category"));
        assertEquals("AGGREGATED_OTHER", result.get(3).get("category"));
    }

    @Test
    @DisplayName("총소비가 0이면 빈 리스트를 반환한다")
    void buildTopCategories_whenTotalExpenseIsZero_returnsEmptyList() {
        MonthlyAiReportOrchestrationService service = createUnitService();
        MonthlyExpenseSummary expense = expenseSummary(
                0L,
                Map.of("FOOD", 100_000L)
        );

        assertTrue(service.buildTopCategories(expense).isEmpty());
    }

    @Test
    @DisplayName("FastAPI에는 전체 카테고리를, 저장에는 요약된 상위 카테고리를 사용한다")
    void runBatch_sendsAllCategoriesToFastApiAndStoresSummarizedCategories()
            throws Exception {
        MonthlyExpenseSummary currentExpense = expenseSummary(
                2_680_000L,
                linkedCategories(
                        entry("FOOD", 760_000L),
                        entry("SHOPPING", 540_000L),
                        entry("TRANSPORTATION", 310_000L),
                        entry("COMMUNICATION", 120_000L),
                        entry("ETC", 950_000L)
                )
        );

        CapturingRecommendationClient recommendationClient =
                new CapturingRecommendationClient();
        CapturingAiReportService aiReportService =
                new CapturingAiReportService();

        MonthlyAiReportOrchestrationService service = createService(
                scope -> List.of(1L),
                (userId, yearMonth) -> null,
                (userId, yearMonth) ->
                        "2026-07".equals(yearMonth)
                                ? currentExpense
                                : null,
                recommendationClient,
                aiReportService
        );

        service.runBatch(YearMonth.of(2026, 7));

        assertNotNull(recommendationClient.capturedRequest);
        JsonNode categoryExpenses = objectMapper.readTree(
                recommendationClient.capturedRequest.getCategoryExpenses()
        );

        assertEquals(5, categoryExpenses.size());
        assertTrue(hasCategory(categoryExpenses, "ETC"));
        assertFalse(hasCategory(categoryExpenses, "AGGREGATED_OTHER"));

        assertNotNull(aiReportService.capturedReport);
        JsonNode financialSummary = objectMapper.readTree(
                aiReportService.capturedReport.getFinancialSummaryJson()
        );
        JsonNode topCategories = financialSummary.get("topCategories");

        assertEquals(4, topCategories.size());
        assertTrue(hasCategory(topCategories, "AGGREGATED_OTHER"));
        assertFalse(hasCategory(topCategories, "ETC"));
    }

    @Test
    @DisplayName("AI_REPORT 저장이 정상 반환한 뒤 자동 이메일 발송을 호출한다")
    void runBatch_deliversAutomaticallyAfterUpsertReturns() {
        List<String> events = new ArrayList<>();
        AiReportService reportService = new CapturingAiReportService() {
            @Override
            public void upsert(AiReport aiReport) {
                events.add("UPSERT:" + aiReport.getUserId());
            }
        };
        AiReportEmailDeliveryService deliveryService = new NoOpAutomaticDeliveryService() {
            @Override
            public void deliverAutomatically(Long userId, String yearMonth) {
                events.add("DELIVERY:" + userId);
            }
        };
        MonthlyAiReportOrchestrationService service = createService(
                scope -> List.of(1L),
                (userId, yearMonth) -> null,
                (userId, yearMonth) -> null,
                new CapturingRecommendationClient(),
                reportService,
                deliveryService
        );

        service.runBatch(YearMonth.of(2026, 7));

        assertEquals(List.of("UPSERT:1", "DELIVERY:1"), events);
    }

    @Test
    @DisplayName("AI_REPORT 저장 실패 시 자동 이메일을 호출하지 않는다")
    void runBatch_whenUpsertFails_doesNotDeliverAutomatically() {
        List<Long> deliveredUserIds = new ArrayList<>();
        AiReportService reportService = new CapturingAiReportService() {
            @Override
            public void upsert(AiReport aiReport) {
                throw new IllegalStateException("simulated persistence failure");
            }
        };
        AiReportEmailDeliveryService deliveryService = new NoOpAutomaticDeliveryService() {
            @Override
            public void deliverAutomatically(Long userId, String yearMonth) {
                deliveredUserIds.add(userId);
            }
        };
        MonthlyAiReportOrchestrationService service = createService(
                scope -> List.of(1L),
                (userId, yearMonth) -> null,
                (userId, yearMonth) -> null,
                new CapturingRecommendationClient(),
                reportService,
                deliveryService
        );

        assertDoesNotThrow(() -> service.runBatch(YearMonth.of(2026, 7)));

        assertTrue(deliveredUserIds.isEmpty());
    }

    @Test
    @DisplayName("첫 사용자의 자동 발송 오류 후에도 다음 사용자를 계속 처리한다")
    void runBatch_whenFirstAutomaticDeliveryFails_continuesWithNextUser() {
        List<Long> savedUserIds = new ArrayList<>();
        List<Long> attemptedDeliveryUserIds = new ArrayList<>();
        AiReportService reportService = new CapturingAiReportService() {
            @Override
            public void upsert(AiReport aiReport) {
                savedUserIds.add(aiReport.getUserId());
            }
        };
        AiReportEmailDeliveryService deliveryService = new NoOpAutomaticDeliveryService() {
            @Override
            public void deliverAutomatically(Long userId, String yearMonth) {
                attemptedDeliveryUserIds.add(userId);
                if (userId.equals(1L)) {
                    throw new IllegalStateException("simulated isolated delivery failure");
                }
            }
        };
        MonthlyAiReportOrchestrationService service = createService(
                scope -> List.of(1L, 2L),
                (userId, yearMonth) -> null,
                (userId, yearMonth) -> null,
                new CapturingRecommendationClient(),
                reportService,
                deliveryService
        );

        assertDoesNotThrow(() -> service.runBatch(YearMonth.of(2026, 7)));

        assertEquals(List.of(1L, 2L), savedUserIds);
        assertEquals(List.of(1L, 2L), attemptedDeliveryUserIds);
    }

    @Test
    @DisplayName("설정된 batch.ai-report.user-scope를 ActiveUserQueryClient에 그대로 전달한다")
    void runBatch_passesConfiguredUserScopeToClient() {
        List<UserScope> requestedScopes = new ArrayList<>();
        MonthlyAiReportOrchestrationService service = createService(
                scope -> {
                    requestedScopes.add(scope);
                    return List.of();
                },
                (userId, yearMonth) -> null,
                (userId, yearMonth) -> null,
                new CapturingRecommendationClient(),
                new CapturingAiReportService()
        );

        service.runBatch(YearMonth.of(2026, 7));

        assertEquals(List.of(UserScope.REAL_ONLY), requestedScopes);
    }

    private MonthlyAiReportOrchestrationService createUnitService() {
        return createService(
                scope -> List.of(),
                (userId, yearMonth) -> null,
                (userId, yearMonth) -> null,
                new CapturingRecommendationClient(),
                new CapturingAiReportService()
        );
    }

    private MonthlyAiReportOrchestrationService createService(
            ActiveUserQueryClient activeUserQueryClient,
            IncomeAnalysisQueryClient incomeAnalysisQueryClient,
            MonthlyExpenseQueryClient monthlyExpenseQueryClient,
            FastApiProductRecommendationClient recommendationClient,
            AiReportService aiReportService
    ) {
        return createService(
                activeUserQueryClient,
                incomeAnalysisQueryClient,
                monthlyExpenseQueryClient,
                recommendationClient,
                aiReportService,
                new NoOpAutomaticDeliveryService()
        );
    }

    private MonthlyAiReportOrchestrationService createService(
            ActiveUserQueryClient activeUserQueryClient,
            IncomeAnalysisQueryClient incomeAnalysisQueryClient,
            MonthlyExpenseQueryClient monthlyExpenseQueryClient,
            FastApiProductRecommendationClient recommendationClient,
            AiReportService aiReportService,
            AiReportEmailDeliveryService deliveryService
    ) {
        return new MonthlyAiReportOrchestrationService(
                activeUserQueryClient,
                new AiReportBatchUserScopeProperties("REAL_ONLY"),
                incomeAnalysisQueryClient,
                monthlyExpenseQueryClient,
                recommendationClient,
                aiReportService,
                deliveryService,
                objectMapper
        );
    }

    private MonthlyExpenseSummary expenseSummary(
            Long totalExpense,
            Map<String, Long> categories
    ) {
        return new MonthlyExpenseSummary(
                1L,
                "2026-07",
                totalExpense,
                0L,
                categories
        );
    }

    @SafeVarargs
    private final Map<String, Long> linkedCategories(
            Map.Entry<String, Long>... entries
    ) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private Map.Entry<String, Long> entry(
            String category,
            Long amount
    ) {
        return new AbstractMap.SimpleEntry<>(category, amount);
    }

    private boolean hasCategory(
            List<Map<String, Object>> categories,
            String expectedCategory
    ) {
        return categories.stream().anyMatch(category ->
                expectedCategory.equals(category.get("category"))
        );
    }

    private boolean hasCategory(
            JsonNode categories,
            String expectedCategory
    ) {
        for (JsonNode category : categories) {
            if (expectedCategory.equals(category.get("category").asText())) {
                return true;
            }
        }
        return false;
    }

    private long sumAmounts(
            List<Map<String, Object>> categories
    ) {
        return categories.stream()
                .mapToLong(category ->
                        ((Number) category.get("amount")).longValue()
                )
                .sum();
    }

    private double sumRatios(
            List<Map<String, Object>> categories
    ) {
        return categories.stream()
                .mapToDouble(category ->
                        ((Number) category.get("ratio")).doubleValue()
                )
                .sum();
    }

    private static class CapturingRecommendationClient
            extends FastApiProductRecommendationClient {
        private ProductRecommendationRequest capturedRequest;

        private CapturingRecommendationClient() {
            super("http://localhost");
        }

        @Override
        public ProductRecommendationResponse recommend(
                ProductRecommendationRequest request
        ) {
            capturedRequest = request;
            return new ProductRecommendationResponse();
        }
    }

    /**
     * getMonthlyIncomeAnalysis(단건)와 getMonthlyIncomeAnalysisBulk(벌크) 호출 횟수를
     * 각각 세는 fake. 람다로는 두 메서드를 다 구현할 수 없어(함수형 인터페이스가 아니게 됨),
     * runBatch가 실제로 벌크 경로를 타는지(default 폴백이 아니라) 검증하려면 명시적 구현이 필요하다.
     */
    private static class RecordingIncomeAnalysisQueryClient
            implements IncomeAnalysisQueryClient {

        private int singleCallCount;
        private int bulkCallCount;
        private List<Long> lastBulkUserIds;

        @Override
        public MonthlyIncomeAnalysisSummary getMonthlyIncomeAnalysis(
                Long userId, YearMonth yearMonth
        ) {
            singleCallCount++;
            return null;
        }

        @Override
        public Map<Long, MonthlyIncomeAnalysisSummary> getMonthlyIncomeAnalysisBulk(
                List<Long> userIds, YearMonth yearMonth
        ) {
            bulkCallCount++;
            lastBulkUserIds = userIds;
            Map<Long, MonthlyIncomeAnalysisSummary> result = new LinkedHashMap<>();
            for (Long userId : userIds) {
                result.put(userId, null);
            }
            return result;
        }
    }

    private static class CapturingAiReportService
            extends AiReportService {

        private AiReport capturedReport;

        private CapturingAiReportService() {
            super(null);
        }

        @Override
        public void upsert(AiReport aiReport) {
            capturedReport = aiReport;
        }
    }

    private static class NoOpAutomaticDeliveryService
            extends AiReportEmailDeliveryService {

        private NoOpAutomaticDeliveryService() {
            super(null, null, null, null, null);
        }

        @Override
        public void deliverAutomatically(Long userId, String yearMonth) {
            // 기존 리포트 생성 테스트에서는 자동 발송을 수행하지 않는다.
        }
    }
}
