package com.ntropy.ai.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.ai.client.fastapi.FastApiTransactionClassificationClient;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResponse;
import com.ntropy.ai.dto.fastapi.TransactionForClassification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 월간 AI 리포트 생성 파이프라인 전체를 조정하는 서비스입니다.
 *
 * 현재는 account-service, diagnosis-service 연동 계약이 일부 확정되지 않았으므로
 * 배치 대상 사용자 조회와 거래 조회는 안전한 빈 목록으로 유지합니다.
 *
 * FastAPI 소비 분류 API 호출 구조만 먼저 연결해두고,
 * 계약 확정 후 processSingleUser() 내부 TODO를 실제 Client 호출로 교체합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyAiReportOrchestrationService {

    // FastAPI 소비 분류 API 호출 Client입니다.
    private final FastApiTransactionClassificationClient classificationClient;

    /**
     * 매월 1일 스케줄러에서 호출됩니다.
     *
     * 예를 들어 2026년 8월 1일에 실행되면
     * 2026년 7월 리포트를 생성 대상으로 처리합니다.
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
     * 테스트에서는 현재 날짜와 관계없이 원하는 연월을 직접 전달할 수 있습니다.
     *
     * @param targetYearMonth 리포트 생성 대상 연월
     */
    public void runBatch(YearMonth targetYearMonth) {
        String yearMonth = targetYearMonth.toString();
        long startedAt = System.currentTimeMillis();

        log.info(
                "[AI 리포트 배치] 대상 연월: {}, 배치 시작",
                yearMonth
        );

        // 추후 user-service 또는 account-service Client를 통해
        // 배치 대상 사용자 목록을 조회하도록 교체합니다.
        List<Long> targetUserIds = findTargetUserIds(yearMonth);

        int successCount = 0;
        int failedCount = 0;

        // 사용자 한 명의 실패가 전체 배치를 중단시키지 않도록 개별 예외를 분리합니다.
        for (Long userId : targetUserIds) {
            try {
                processSingleUser(userId, yearMonth);
                successCount++;

            } catch (Exception exception) {
                failedCount++;

                log.error(
                        "[AI 리포트 배치] 사용자 리포트 생성 실패 - userId: {}, yearMonth: {}",
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
     * 아직 사용자 조회 Client 계약이 없으므로 빈 목록을 반환합니다.
     * 따라서 현재 단계에서 실제 데이터 저장이나 외부 API 호출은 일어나지 않습니다.
     */
    private List<Long> findTargetUserIds(String yearMonth) {
        log.info(
                "[AI 리포트 배치] 대상 사용자 조회 연동 대기 중 - yearMonth: {}",
                yearMonth
        );

        return Collections.emptyList();
    }

    /**
     * 사용자 한 명의 월간 AI 리포트를 생성합니다.
     *
     * 현재는 account-service 거래 조회 계약이 확정되지 않았으므로
     * 분류 대상 거래 목록은 빈 목록으로 둡니다.
     *
     * 이후 account-service 거래 조회 Client가 생기면
     * transactions 변수에 실제 분류 대상 출금 거래를 넣으면 됩니다.
     */
    private void processSingleUser(
            Long userId,
            String yearMonth
    ) {
        log.info(
                "[AI 리포트 배치] 사용자 리포트 처리 시작 - userId: {}, yearMonth: {}",
                userId,
                yearMonth
        );

        /*
         * TODO 1. account-service에서 분류 대상 거래 조회
         *
         * 현재는 거래 조회 Client 계약이 없으므로 빈 목록으로 둡니다.
         */
        List<TransactionForClassification> transactions = List.of();

        if (transactions.isEmpty()) {
            log.info(
                    "[AI 리포트 배치] 분류 대상 거래 없음 - userId: {}, yearMonth: {}",
                    userId,
                    yearMonth
            );
            return;
        }

        /*
         * TODO 2. FastAPI에 소비 내역 분류 요청
         */
        TransactionClassificationResponse classificationResponse =
                classificationClient.classifyTransactions(transactions);

        if (
                classificationResponse == null
                        || !Boolean.TRUE.equals(classificationResponse.getSuccess())
                        || classificationResponse.getData() == null
        ) {
            throw new IllegalStateException(
                    "FastAPI 소비 분류 응답이 올바르지 않습니다."
            );
        }

        log.info(
                "[AI 리포트 배치] 소비 분류 완료 - userId: {}, yearMonth: {}, resultCount: {}",
                userId,
                yearMonth,
                classificationResponse.getData().getResults().size()
        );

        /*
         * TODO 3. account-service에 TXN_ANALYSIS 저장 요청
         *
         * TODO 4. diagnosis-service에 재무진단 생성 요청
         *
         * TODO 5. diagnosis-service에서 DIAGNOSIS_RESULT 조회
         *
         * TODO 6. FastAPI에 금융상품 추천 및 리포트 문구 생성 요청
         *
         * TODO 7. AiReportService.upsert()로 AI_REPORT 저장 또는 갱신
         *
         * TODO 8. notification-service에 알림 발송 요청
         */

        log.info(
                "[AI 리포트 배치] 사용자 리포트 처리 뼈대 완료 - userId: {}, yearMonth: {}",
                userId,
                yearMonth
        );
    }
}