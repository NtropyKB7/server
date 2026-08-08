package com.ntropy.ai.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.ai.client.fastapi.FastApiTransactionClassificationClient;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResponse;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResult;
import com.ntropy.ai.dto.fastapi.TransactionForClassification;
import com.ntropy.common.client.AccountTransactionAnalysisClient;
import com.ntropy.common.dto.account.ClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveItem;
import com.ntropy.common.dto.account.TransactionAnalysisSaveRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyAiReportOrchestrationService {

    private final FastApiTransactionClassificationClient classificationClient;

    private final AccountTransactionAnalysisClient
            accountTransactionAnalysisClient;

    /**
     * 매월 1일 실행되며 지난달 데이터를 처리합니다.
     */
    public void runLastMonthBatch() {
        YearMonth lastMonth = YearMonth.now(
                ZoneId.of("Asia/Seoul")
        ).minusMonths(1);

        runBatch(lastMonth);
    }

    /**
     * 지정한 연월의 배치를 실행합니다.
     */
    public void runBatch(YearMonth targetYearMonth) {
        if (targetYearMonth == null) {
            throw new IllegalArgumentException(
                    "배치 대상 연월은 필수입니다."
            );
        }

        String yearMonth = targetYearMonth.toString();
        long startedAt = System.currentTimeMillis();

        log.info(
                "[AI 리포트 배치] 대상 연월: {}, 배치 시작",
                yearMonth
        );

        List<Long> targetUserIds = findTargetUserIds(yearMonth);

        int successCount = 0;
        int failedCount = 0;

        for (Long userId : targetUserIds) {
            try {
                processSingleUser(userId, yearMonth);
                successCount++;
            } catch (Exception exception) {
                failedCount++;

                log.error(
                        "[AI 리포트 배치] 사용자 처리 실패 - userId: {}, yearMonth: {}",
                        userId,
                        yearMonth,
                        exception
                );
            }
        }

        long elapsedMillis =
                System.currentTimeMillis() - startedAt;

        log.info(
                "[AI 리포트 배치] 완료 - yearMonth: {}, 대상: {}명, 성공: {}명, 실패: {}명, 실행 시간: {}ms",
                yearMonth,
                targetUserIds.size(),
                successCount,
                failedCount,
                elapsedMillis
        );
    }

    /**
     * 현재는 사용자 조회 Client가 연결되지 않아 빈 목록을 반환합니다.
     */
    private List<Long> findTargetUserIds(String yearMonth) {
        log.info(
                "[AI 리포트 배치] 대상 사용자 조회 연동 대기 중 - yearMonth: {}",
                yearMonth
        );

        return Collections.emptyList();
    }

    /**
     * 한 사용자의 거래 조회 및 소비 분류 결과 저장을 처리합니다.
     */
    private void processSingleUser(
            Long userId,
            String yearMonth
    ) {
        log.info(
                "[AI 리포트 배치] 사용자 처리 시작 - userId: {}, yearMonth: {}",
                userId,
                yearMonth
        );

        List<ClassificationTargetTransaction>
                classificationTargets =
                accountTransactionAnalysisClient
                        .findClassificationTargets(
                                userId,
                                yearMonth
                        );

        if (classificationTargets == null
                || classificationTargets.isEmpty()) {
            log.info(
                    "[AI 리포트 배치] 분류 대상 거래 없음 - userId: {}, yearMonth: {}",
                    userId,
                    yearMonth
            );
            return;
        }

        List<TransactionForClassification> transactions =
                classificationTargets.stream()
                        .map(target -> new TransactionForClassification(
                                target.getTransactionId(),
                                target.getMerchantName(),
                                target.getDescription(),
                                target.getAmount(),
                                target.getTransactionDate()
                        ))
                        .toList();

        // FastAPI 소비 분류 요청
        TransactionClassificationResponse
                classificationResponse =
                classificationClient.classifyTransactions(
                        transactions
                );

        if (classificationResponse == null
                || !Boolean.TRUE.equals(
                classificationResponse.getSuccess()
        )
                || classificationResponse.getData() == null
                || classificationResponse.getData()
                .getResults() == null) {
            throw new IllegalStateException(
                    "FastAPI 소비 분류 응답이 올바르지 않습니다."
            );
        }

        List<TransactionClassificationResult> results =
                classificationResponse.getData().getResults();

        // 입력 거래 수와 응답 결과 수 검증
        if (results.size() != transactions.size()) {
            throw new IllegalStateException(
                    "FastAPI 분류 결과 수가 요청 거래 수와 일치하지 않습니다."
            );
        }

        // transactionId 1:1 매핑 검증
        Set<Long> requestedTransactionIds =
                transactions.stream()
                        .map(TransactionForClassification
                                ::getTransactionId)
                        .collect(Collectors.toSet());

        Set<Long> responseTransactionIds =
                results.stream()
                        .map(TransactionClassificationResult
                                ::getTransactionId)
                        .collect(Collectors.toSet());

        if (!requestedTransactionIds.equals(
                responseTransactionIds
        )) {
            throw new IllegalStateException(
                    "FastAPI 분류 결과의 transactionId가 요청 거래와 일치하지 않습니다."
            );
        }

        // account-service 저장용 DTO로 변환
        List<TransactionAnalysisSaveItem> saveItems =
                results.stream()
                        .map(result -> new TransactionAnalysisSaveItem(
                                result.getTransactionId(),
                                result.getIsConsumption(),
                                result.getCategory(),
                                result.getExpenseType()
                        ))
                        .toList();

        // TXN_ANALYSIS upsert 저장
        accountTransactionAnalysisClient
                .saveTransactionAnalyses(
                        new TransactionAnalysisSaveRequest(
                                userId,
                                yearMonth,
                                saveItems
                        )
                );

        log.info(
                "[AI 리포트 배치] TXN_ANALYSIS 저장 완료 - userId: {}, yearMonth: {}, savedCount: {}",
                userId,
                yearMonth,
                saveItems.size()
        );

        /*
         * TODO 1. diagnosis-service 진단 재계산 요청
         * TODO 2. DIAGNOSIS_RESULT 조회
         * TODO 3. FastAPI 종합 분석 및 추천 요청
         * TODO 4. AI_REPORT upsert 저장
         * TODO 5. 이메일·카카오톡 알림 발송
         */

        log.info(
                "[AI 리포트 배치] 사용자 처리 완료 - userId: {}, yearMonth: {}",
                userId,
                yearMonth
        );
    }
}