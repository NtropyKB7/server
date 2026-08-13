package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.ai.client.fastapi.FastApiProductRecommendationClient;
import com.ntropy.ai.domain.AiReport;
import com.ntropy.ai.dto.fastapi.ProductRecommendationRequest;
import com.ntropy.ai.dto.fastapi.ProductRecommendationResponse;
import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.IncomeAnalysisQueryClient;
import com.ntropy.common.client.MonthlyExpenseQueryClient;
import com.ntropy.common.dto.account.MonthlyExpenseSummary;

class MonthlyAiReportOrchestrationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runBatch_whenTargetUserListIsEmpty_completesWithoutException() {
        MonthlyAiReportOrchestrationService orchestrationService =
                createService(
                        List::of,
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
    void buildTopCategories_whenTotalExpenseIsZero_returnsEmptyList() {
        MonthlyAiReportOrchestrationService service = createUnitService();
        MonthlyExpenseSummary expense = expenseSummary(
                0L,
                Map.of("FOOD", 100_000L)
        );

        assertTrue(service.buildTopCategories(expense).isEmpty());
    }

    @Test
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
                () -> List.of(1L),
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

    private MonthlyAiReportOrchestrationService createUnitService() {
        return createService(
                List::of,
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
        return new MonthlyAiReportOrchestrationService(
                activeUserQueryClient,
                incomeAnalysisQueryClient,
                monthlyExpenseQueryClient,
                recommendationClient,
                aiReportService,
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
}