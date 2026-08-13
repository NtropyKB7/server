package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.ai.client.fastapi.FastApiTransactionClassificationClient;
import com.ntropy.ai.dto.fastapi.TransactionClassificationData;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResponse;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResult;
import com.ntropy.ai.dto.fastapi.TransactionForClassification;
import com.ntropy.common.client.AccountTransactionAnalysisClient;
import com.ntropy.common.dto.account.ClassificationTargetTransaction;
import com.ntropy.common.dto.account.DailyClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveItem;
import com.ntropy.common.dto.account.TransactionAnalysisSaveRequest;

class DailyTransactionClassificationServiceTest {

    @Test
    void savesDeterministicAndFastApiResultsAndUsesFallbackForMissingResult() {
        FakeAccountClient accountClient =
                new FakeAccountClient(
                        List.of(
                                target(
                                        1L,
                                        "ORDINARY",
                                        "삼성생명 실손보험"
                                ),
                                target(
                                        2L,
                                        "ORDINARY",
                                        "스타벅스"
                                ),
                                target(
                                        3L,
                                        "ORDINARY",
                                        "알 수 없는 상점"
                                )
                        )
                );

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();

        /*
         * FastAPI가 2번 거래 결과만 반환하도록 구성합니다.
         * 누락된 3번 거래는 ETC / VARIABLE로 저장되어야 합니다.
         */
        fastApiClient.results = List.of(
                new TransactionClassificationResult(
                        2L,
                        true,
                        "FOOD",
                        "VARIABLE"
                )
        );

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        fastApiClient,
                        new TransactionPreClassificationService()
                );

        assertEquals(
                3,
                service.run()
        );

        /*
         * 보험료는 Spring에서 결정되므로 FastAPI에는
         * 나머지 두 거래만 전달됩니다.
         */
        assertEquals(
                2,
                fastApiClient.requested.size()
        );

        assertSaved(
                accountClient.saved,
                1L,
                "INSURANCE",
                "FIXED"
        );

        assertSaved(
                accountClient.saved,
                2L,
                "FOOD",
                "VARIABLE"
        );

        assertSaved(
                accountClient.saved,
                3L,
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void splitsFastApiRequestsIntoBatchesOfAtMostOneHundred() {
        List<DailyClassificationTargetTransaction> targets =
                new ArrayList<>();

        for (long id = 1; id <= 101; id++) {
            targets.add(
                    target(
                            id,
                            "ORDINARY",
                            "알 수 없는 상점 " + id
                    )
            );
        }

        FakeAccountClient accountClient =
                new FakeAccountClient(targets);

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        fastApiClient,
                        new TransactionPreClassificationService()
                );

        assertEquals(
                101,
                service.run()
        );

        assertEquals(
                List.of(100, 1),
                fastApiClient.requestSizes
        );

        assertEquals(
                101,
                accountClient.saved.size()
        );
    }

    @Test
    void invalidFastApiResultUsesFallback() {
        FakeAccountClient accountClient =
                new FakeAccountClient(
                        List.of(
                                target(
                                        1L,
                                        "ORDINARY",
                                        "알 수 없는 상점"
                                )
                        )
                );

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();

        /*
         * 허용하지 않는 category와 expenseType을 반환하면
         * 해당 응답을 신뢰하지 않고 fallback합니다.
         */
        fastApiClient.results = List.of(
                new TransactionClassificationResult(
                        1L,
                        true,
                        "UNKNOWN",
                        "SOMETIMES"
                )
        );

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        fastApiClient,
                        new TransactionPreClassificationService()
                );

        service.run();

        assertSaved(
                accountClient.saved,
                1L,
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void continuesUntilAccountServiceReturnsNoMorePages() {
        FakeAccountClient accountClient =
                new FakeAccountClient(
                        List.of(
                                List.of(
                                        target(
                                                1L,
                                                "INSTALLMENT",
                                                "정기적금"
                                        )
                                ),
                                List.of(
                                        target(
                                                2L,
                                                "LOAN",
                                                "대출상환"
                                        )
                                )
                        ),
                        true
                );

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        new FakeFastApiClient(),
                        new TransactionPreClassificationService()
                );

        assertEquals(
                2,
                service.run()
        );

        /*
         * 첫 페이지, 두 번째 페이지, 종료 확인까지 총 세 번 조회합니다.
         */
        assertEquals(
                3,
                accountClient.queryCalls
        );

        assertEquals(
                2,
                accountClient.saved.size()
        );
    }

    private void assertSaved(
            List<TransactionAnalysisSaveItem> saved,
            Long transactionId,
            String expectedCategory,
            String expectedExpenseType
    ) {
        TransactionAnalysisSaveItem item = saved.stream()
                .filter(
                        value -> transactionId.equals(
                                value.getTransactionId()
                        )
                )
                .findFirst()
                .orElseThrow();

        assertEquals(
                expectedCategory,
                item.getCategory()
        );

        assertEquals(
                expectedExpenseType,
                item.getExpenseType()
        );
    }

    private DailyClassificationTargetTransaction target(
            Long transactionId,
            String transactionCategory,
            String desc3
    ) {
        long outAmount =
                "INSTALLMENT".equals(transactionCategory)
                        ? 0L
                        : 10_000L;

        long inAmount =
                "INSTALLMENT".equals(transactionCategory)
                        ? 10_000L
                        : 0L;

        return new DailyClassificationTargetTransaction(
                transactionId,
                10L,
                transactionCategory,
                outAmount,
                inAmount,
                "0004",
                null,
                null,
                "체크",
                desc3,
                null
        );
    }

    private static class FakeFastApiClient
            extends FastApiTransactionClassificationClient {

        private List<TransactionForClassification> requested =
                List.of();

        private List<TransactionClassificationResult> results =
                List.of();

        private final List<Integer> requestSizes =
                new ArrayList<>();

        @Override
        public TransactionClassificationResponse classifyTransactions(
                List<TransactionForClassification> transactions
        ) {
            requested = List.copyOf(transactions);
            requestSizes.add(transactions.size());

            return new TransactionClassificationResponse(
                    true,
                    200,
                    "ok",
                    new TransactionClassificationData(results)
            );
        }
    }

    private static class FakeAccountClient
            implements AccountTransactionAnalysisClient {

        private final List<
                List<DailyClassificationTargetTransaction>
                > pages;

        private int pageIndex;
        private int queryCalls;

        private final List<TransactionAnalysisSaveItem> saved =
                new ArrayList<>();

        private FakeAccountClient(
                List<DailyClassificationTargetTransaction> nextPage
        ) {
            this.pages = List.of(
                    List.copyOf(nextPage)
            );
        }

        private FakeAccountClient(
                List<List<DailyClassificationTargetTransaction>> pages,
                boolean multiplePages
        ) {
            this.pages = pages.stream()
                    .map(List::copyOf)
                    .toList();
        }

        @Override
        public List<DailyClassificationTargetTransaction>
        findUnanalyzedTransactions(int limit) {
            queryCalls++;

            if (pageIndex >= pages.size()) {
                return List.of();
            }

            return pages.get(pageIndex++);
        }

        @Override
        public void saveDailyTransactionAnalyses(
                List<TransactionAnalysisSaveItem> analyses
        ) {
            saved.addAll(analyses);
        }

        @Override
        public List<ClassificationTargetTransaction>
        findClassificationTargets(
                Long userId,
                String yearMonth
        ) {
            return List.of();
        }

        @Override
        public void saveTransactionAnalyses(
                TransactionAnalysisSaveRequest request
        ) {
            // 기존 월간 분류 계약은 이 테스트에서 사용하지 않습니다.
        }
    }
}