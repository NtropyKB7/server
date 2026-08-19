package com.ntropy.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ntropy.common.domain.LoanDisbursementKeywords;
import com.ntropy.common.dto.account.DailyClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveItem;

/**
 * FastAPI 호출 전에 거래 유형과 명확한 키워드를 이용해
 * 소비 분석 결과를 결정합니다.
 */
@Service
public class TransactionPreClassificationService {

    private static final List<String> FINANCIAL_TRANSFER_KEYWORDS =
            List.of(
                    "정기예금",
                    "정기적금",
                    "자유적금",
                    "적금납입",
                    "예금납입",
                    "대출상환",
                    "원금상환",
                    "대출계좌",
                    "수시입출금계좌",
                    "본인계좌",
                    "내계좌"
            );

    private static final List<String> CARD_BILL_KEYWORDS =
            List.of(
                    "카드대금",
                    "신용카드대금",
                    "카드이용대금",
                    "카드결제대금",
                    "카드청구"
            );

    private static final List<String> CASH_WITHDRAWAL_KEYWORDS =
            List.of(
                    "현금인출",
                    "ATM출금",
                    "ATM인출",
                    "스마트출금",
                    "CD출금",
                    "자동화기기출금"
            );

    private static final List<String> INSURANCE_KEYWORDS =
            List.of(
                    "보험",
                    "보험료",
                    "생명",
                    "화재",
                    "손해보험",
                    "해상보험",
                    "실손보험",
                    "건강보험",
                    "운전자보험",
                    "암보험",
                    "종신보험",
                    "연금보험"
            );

    /**
     * Spring에서 결과를 확정할 수 있으면 분석 결과를 반환하고,
     * FastAPI 분류가 필요하면 Optional.empty()를 반환합니다.
     */
    public Optional<TransactionAnalysisSaveItem> classify(
            DailyClassificationTargetTransaction transaction
    ) {
        String transactionCategory = normalize(
                transaction.getTransactionCategory()
        );

        /*
         * LOAN 신규·실행·증액은 대출금 지급 거래이므로 비소비입니다.
         * 판정 키워드는 LoanDisbursementKeywords(common)를 MonthlyExpenseMapper·
         * FinancialCommitmentMapper의 SQL 판정과 공유합니다.
         *
         * 그 외 LOAN 거래는 정상 상환으로 보고 FINANCE / FIXED 소비로 분류합니다.
         * 여기서는 분류 정보(is_consumption/category/expense_type)만 저장하며 금액은
         * 저장하지 않습니다. 월간 집계 금액(원금 제외, 이자만 소비)은 MonthlyExpenseMapper가
         * ACCOUNT_TRANSACTION에서 별도로 결정적으로 계산합니다.
         */
        if ("LOAN".equals(transactionCategory)) {
            if (LoanDisbursementKeywords.matches(
                    transaction.getLoanTransactionTypeName()
            )) {
                return Optional.of(
                        nonConsumption(transaction.getTransactionId())
                );
            }

            return Optional.of(
                    consumption(
                            transaction.getTransactionId(),
                            "FINANCE",
                            "FIXED"
                    )
            );
        }

        /*
         * INSTALLMENT 계좌로 들어온 납입액은
         * FINANCE / FIXED 소비로 처리합니다.
         */
        if ("INSTALLMENT".equals(transactionCategory)) {
            return Optional.of(
                    consumption(
                            transaction.getTransactionId(),
                            "FINANCE",
                            "FIXED"
                    )
            );
        }

        /*
         * 조회 SQL은 ORDINARY, INSTALLMENT, LOAN만 반환하지만,
         * 예상하지 못한 거래 유형을 FastAPI로 보내지 않도록 방어합니다.
         */
        if (!"ORDINARY".equals(transactionCategory)) {
            return Optional.of(
                    nonConsumption(transaction.getTransactionId())
            );
        }

        String description = joinedDescription(transaction);

        /*
         * 적금·예금·대출계좌 등 금융 목적이 명확한 ORDINARY 출금은
         * INSTALLMENT 또는 LOAN 거래와 중복 집계되지 않도록
         * 비소비 처리합니다.
         */
        if (containsAny(
                description,
                FINANCIAL_TRANSFER_KEYWORDS
        )) {
            return Optional.of(
                    nonConsumption(transaction.getTransactionId())
            );
        }

        /*
         * 카드대금은 금액이 카드 사용량에 따라 달라지므로
         * ETC / VARIABLE로 처리합니다.
         */
        if (containsAny(
                description,
                CARD_BILL_KEYWORDS
        )) {
            return Optional.of(
                    consumption(
                            transaction.getTransactionId(),
                            "ETC",
                            "VARIABLE"
                    )
            );
        }

        /*
         * 사용처를 알 수 없는 현금 인출은
         * ETC / VARIABLE로 처리합니다.
         */
        if (containsAny(
                description,
                CASH_WITHDRAWAL_KEYWORDS
        )) {
            return Optional.of(
                    consumption(
                            transaction.getTransactionId(),
                            "ETC",
                            "VARIABLE"
                    )
            );
        }

        /*
         * 명확한 보험료는 INSURANCE / FIXED로 처리합니다.
         */
        if (containsAny(
                description,
                INSURANCE_KEYWORDS
        )) {
            return Optional.of(
                    consumption(
                            transaction.getTransactionId(),
                            "INSURANCE",
                            "FIXED"
                    )
            );
        }

        /*
         * 나머지 ORDINARY 출금 거래만 FastAPI 분류 대상으로 남깁니다.
         */
        return Optional.empty();
    }

    /**
     * desc3을 우선 배치하고 나머지 필드는 보조 정보로 연결합니다.
     */
    private String joinedDescription(
            DailyClassificationTargetTransaction transaction
    ) {
        return String.join(
                " ",
                List.of(
                        valueOrEmpty(transaction.getDesc3()),
                        valueOrEmpty(transaction.getDesc1()),
                        valueOrEmpty(transaction.getDesc2()),
                        valueOrEmpty(transaction.getDesc4())
                )
        );
    }

    private boolean containsAny(
            String value,
            List<String> keywords
    ) {
        String normalizedValue = normalize(value);

        return keywords.stream()
                .map(this::normalize)
                .anyMatch(normalizedValue::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private TransactionAnalysisSaveItem consumption(
            Long transactionId,
            String category,
            String expenseType
    ) {
        return new TransactionAnalysisSaveItem(
                transactionId,
                true,
                category,
                expenseType
        );
    }

    private TransactionAnalysisSaveItem nonConsumption(
            Long transactionId
    ) {
        return new TransactionAnalysisSaveItem(
                transactionId,
                false,
                null,
                null
        );
    }
}