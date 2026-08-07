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

/**
 * 월간 AI 리포트 생성 파이프라인 전체를 조정하는 서비스입니다.
 *
 * 현재는 대상 사용자 조회 계약이 완전히 연결되지 않아,
 * 사용자 목록 조회는 placeholder 상태입니다.
 * 다만 사용자 1명 단위의 거래 조회 -> FastAPI 분류 -> account-service 저장 흐름은 연결했습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyAiReportOrchestrationService {

    private final FastApiTransactionClassificationClient classificationClient;
    private final AccountTransactionAnalysisClient accountTransactionAnalysisClient;

    /**
     * 매월 1일 실행될 때 지난달 기준으로 배치를 돌립니다.
     */
    public void runLastMonthBatch() {
        YearMonth lastMonth = YearMonth.now(
                ZoneId.of("Asia/Seoul")
        ).minusMonths(1);

        runBatch(lastMonth);
    }

    /**
     * 지정한 연월의 AI 리포트 배치를 실행합니다.
     *
     * @param targetYearMonth 배치 대상 연월
     */
    public void runBatch(YearMonth targetYearMonth) {
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

        long elapsedMillis = System.currentTimeMillis() - startedAt;

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
     * 배치 대상 사용자 ID 목록을 조회합니다.
     *
     * 아직 사용자 목록 조회 Client 계약이 완전히 연결되지 않았으므로
     * 현재는 빈 목록을 반환합니다.
     */
    private List<Long> findTargetUserIds(String yearMonth) {
        log.info(
                "[AI 리포트 배치] 대상 사용자 조회 연동 대기 중 - yearMonth: {}",
                yearMonth
        );

        return Collections.emptyList();
    }

    /**
     * 사용자 1명의 월간 AI 리포트 생성 흐름을 처리합니다.
     *
     * 순서:
     * 1. account-service에서 분류 대상 거래 조회
     * 2. FastAPI 소비 분류 요청
     * 3. account-service에 TXN_ANALYSIS 저장
     * 4. 이후 diagnosis-service / AI_REPORT / 알림 발송은 TODO
     */
    private void processSingleUser(Long userId, String yearMonth) {
        log.info(
                "[AI 리포트 배치] 사용자 리포트 처리 시작 - userId: {}, yearMonth: {}",
                userId,
                yearMonth
        );

        List<ClassificationTargetTransaction> classificationTargets =
                accountTransactionAnalysisClient.findClassificationTargets(
                        userId,
                        yearMonth
                );

        if (classificationTargets.isEmpty()) {
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

        TransactionClassificationResponse classificationResponse =
                classificationClient.classifyTransactions(transactions);

        if (classificationResponse == null
                || !Boolean.TRUE.equals(classificationResponse.getSuccess())
                || classificationResponse.getData() == null
                || classificationResponse.getData().getResults() == null) {
            throw new IllegalStateException(
                    "FastAPI 소비 분류 응답이 올바르지 않습니다."
            );
        }

        List<TransactionClassificationResult> results =
                classificationResponse.getData().getResults();

        if (results.size() != transactions.size()) {
            throw new IllegalStateException(
                    "FastAPI 분류 결과 수가 요청 거래 수와 일치하지 않습니다."
            );
        }

        Set<Long> requestedTransactionIds =
                transactions.stream()
                        .map(TransactionForClassification::getTransactionId)
                        .collect(Collectors.toSet());

        Set<Long> responseTransactionIds =
                results.stream()
                        .map(TransactionClassificationResult::getTransactionId)
                        .collect(Collectors.toSet());

        if (!requestedTransactionIds.equals(responseTransactionIds)) {
            throw new IllegalStateException(
                    "FastAPI 분류 결과의 transactionId가 요청 거래와 일치하지 않습니다."
            );
        }

        List<TransactionAnalysisSaveItem> saveItems =
                results.stream()
                        .map(result -> new TransactionAnalysisSaveItem(
                                result.getTransactionId(),
                                result.getIsConsumption(),
                                result.getCategory(),
                                result.getExpenseType()
                        ))
                        .toList();

        accountTransactionAnalysisClient.saveTransactionAnalyses(
                new TransactionAnalysisSaveRequest(
                        userId,
                        yearMonth,
                        saveItems
                )
        );

        log.info(
                "[AI 리포트 배치] 거래 분석 결과 저장 완료 - userId: {}, yearMonth: {}, savedCount: {}",
                userId,
                yearMonth,
                saveItems.size()
        );

        /*
         * TODO 4. diagnosis-service에 재무진단 생성 요청
         *
         * TODO 5. diagnosis-service에서 DIAGNOSIS_RESULT 조회
         *
         * TODO 6. FastAPI에 금융상품 추천 및 리포트 문구 생성 요청
         *
         * TODO 7. AI_REPORT 저장 또는 갱신
         *
         * TODO 8. 이메일·카카오톡 발송 요청
         */

        log.info(
                "[AI 리포트 배치] 사용자 리포트 처리 완료 - userId: {}, yearMonth: {}",
                userId,
                yearMonth
        );
    }
}