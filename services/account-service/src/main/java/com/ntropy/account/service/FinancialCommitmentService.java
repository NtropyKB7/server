package com.ntropy.account.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialCommitmentMapper;
import com.ntropy.account.mapper.projection.InsuranceOutflowRow;
import com.ntropy.account.mapper.projection.LoanCommitmentCandidateRow;
import com.ntropy.account.mapper.projection.SavingCommitmentCandidateRow;
import com.ntropy.common.dto.account.FinancialCommitmentSummary;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/** 방어모드용 적금 납입·대출 상환·보험료 납입 예정을 계좌·거래 데이터로부터 추정한다. */
@Service
@RequiredArgsConstructor
public class FinancialCommitmentService {

    private static final int OBSERVATION_MONTHS = 3;
    private static final String STATUS_ESTIMATED = "ESTIMATED";
    private static final String STATUS_INSUFFICIENT = "INSUFFICIENT";
    private static final String EXPENSE_TYPE_SAVING = "SAVING_PAYMENT";
    private static final String EXPENSE_TYPE_LOAN = "LOAN_REPAYMENT";
    private static final String EXPENSE_TYPE_INSURANCE = "INSURANCE_PREMIUM";

    private final FinancialCommitmentMapper financialCommitmentMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FinancialCommitmentSummary> findFinancialCommitments(Long userId, LocalDate fromDate, LocalDate toDate) {
        validate(userId, fromDate, toDate);

        List<FinancialCommitmentSummary> commitments = new ArrayList<>();
        commitments.addAll(buildSavingCommitments(userId, fromDate, toDate));
        commitments.addAll(buildLoanCommitments(userId, fromDate, toDate));
        commitments.addAll(buildInsuranceCommitments(userId, fromDate, toDate));

        commitments.sort(Comparator
                .comparing(FinancialCommitmentSummary::getNextPaymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FinancialCommitmentSummary::getExpenseType)
                .thenComparing(FinancialCommitmentSummary::getAccountId));
        return commitments;
    }

    private void validate(Long userId, LocalDate fromDate, LocalDate toDate) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "userId는 양수여야 합니다.");
        }
        if (fromDate == null || toDate == null) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "fromDate, toDate는 필수입니다.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "fromDate는 toDate보다 늦을 수 없습니다.");
        }
    }

    private List<FinancialCommitmentSummary> buildSavingCommitments(Long userId, LocalDate fromDate, LocalDate toDate) {
        List<FinancialCommitmentSummary> result = new ArrayList<>();
        for (SavingCommitmentCandidateRow row : financialCommitmentMapper.findSavingCommitmentCandidates(userId)) {
            if (!withinRangeOrUnknown(row.getNextPaymentDate(), fromDate, toDate)) {
                continue;
            }
            AmountResolution amount = resolveExpectedAmount(row.getExpectedAmount());
            DateResolution date = resolveDate(row.getNextPaymentDate());
            result.add(new FinancialCommitmentSummary(
                    null, row.getAccountId(), EXPENSE_TYPE_SAVING, row.getProductName(),
                    null, amount.amount(), date.date(), amount.status(), date.status()
            ));
        }
        return result;
    }

    private List<FinancialCommitmentSummary> buildLoanCommitments(Long userId, LocalDate fromDate, LocalDate toDate) {
        List<FinancialCommitmentSummary> result = new ArrayList<>();
        for (LoanCommitmentCandidateRow row : financialCommitmentMapper.findLoanCommitmentCandidates(userId)) {
            if (!withinRangeOrUnknown(row.getNextPaymentDate(), fromDate, toDate)) {
                continue;
            }
            AmountResolution amount = resolveExpectedAmount(row.getExpectedAmount());
            DateResolution date = resolveDate(row.getNextPaymentDate());
            Long outstandingBalance = resolveAmount(row.getOutstandingBalance()).amount();
            result.add(new FinancialCommitmentSummary(
                    null, row.getAccountId(), EXPENSE_TYPE_LOAN, row.getProductName(),
                    outstandingBalance, amount.amount(), date.date(), amount.status(), date.status()
            ));
        }
        return result;
    }

    private List<FinancialCommitmentSummary> buildInsuranceCommitments(Long userId, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate observationEnd = toDate.isBefore(today) ? toDate : today;
        LocalDate observationStart = observationEnd.minusMonths(OBSERVATION_MONTHS);

        List<InsuranceOutflowRow> rows = financialCommitmentMapper.findInsuranceOutflowCandidates(
                userId, observationStart, observationEnd);

        Map<InsuranceGroupKey, List<InsuranceOutflowRow>> grouped = new LinkedHashMap<>();
        for (InsuranceOutflowRow row : rows) {
            String rawGroupKey = buildRawGroupKey(row);
            if (rawGroupKey == null || row.getOutAmount() == null) {
                continue;
            }
            InsuranceGroupKey key = new InsuranceGroupKey(row.getAccountId(), normalize(rawGroupKey), row.getOutAmount());
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(row);
        }

        List<FinancialCommitmentSummary> result = new ArrayList<>();
        for (Map.Entry<InsuranceGroupKey, List<InsuranceOutflowRow>> entry : grouped.entrySet()) {
            List<InsuranceOutflowRow> occurrences = entry.getValue();
            long distinctTranDates = occurrences.stream().map(InsuranceOutflowRow::getTranDate).distinct().count();
            if (distinctTranDates < 2) {
                continue;
            }

            InsuranceOutflowRow latest = occurrences.stream()
                    .max(Comparator.comparing(InsuranceOutflowRow::getTranDate))
                    .orElseThrow();
            LocalDate nextPaymentDate = latest.getTranDate().plusMonths(1);
            if (!withinRangeOrUnknown(nextPaymentDate, fromDate, toDate)) {
                continue;
            }

            AmountResolution amount = resolveExpectedAmount(entry.getKey().amount());
            String productName = firstNonBlank(latest.getDesc1(), latest.getDesc2(), latest.getDesc3(), latest.getDesc4());
            result.add(new FinancialCommitmentSummary(
                    null, entry.getKey().accountId(), EXPENSE_TYPE_INSURANCE, productName,
                    null, amount.amount(), nextPaymentDate, amount.status(), STATUS_ESTIMATED
            ));
        }
        return result;
    }

    /** desc1→desc2→desc3→desc4 순서로 trim한 비어 있지 않은 값을 |로 결합한다. 전부 비어 있으면 null. */
    private static String buildRawGroupKey(InsuranceOutflowRow row) {
        List<String> parts = new ArrayList<>();
        for (String value : new String[]{row.getDesc1(), row.getDesc2(), row.getDesc3(), row.getDesc4()}) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    parts.add(trimmed);
                }
            }
        }
        return parts.isEmpty() ? null : String.join("|", parts);
    }

    private static String normalize(String rawGroupKey) {
        return rawGroupKey.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    /** nextPaymentDate가 없으면 기간과 무관하게 포함하고, 있으면 [fromDate, toDate]에 포함될 때만 포함한다. */
    private static boolean withinRangeOrUnknown(LocalDate nextPaymentDate, LocalDate fromDate, LocalDate toDate) {
        if (nextPaymentDate == null) {
            return true;
        }
        return !nextPaymentDate.isBefore(fromDate) && !nextPaymentDate.isAfter(toDate);
    }

    /**
     * DECIMAL(18,2) 원시값을 원 단위 Long으로 변환한다. null·소수부 존재·overflow·음수는
     * 이 항목 하나만 INSUFFICIENT로 대체하고 나머지 응답에는 영향을 주지 않는다.
     */
    private static AmountResolution resolveAmount(BigDecimal raw) {
        if (raw == null) {
            return AmountResolution.insufficient();
        }
        try {
            long won = raw.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return won < 0 ? AmountResolution.insufficient() : AmountResolution.estimated(won);
        } catch (ArithmeticException e) {
            return AmountResolution.insufficient();
        }
    }

    /** 예상 납입액은 실제 지출로 사용할 수 있는 양수일 때만 추정값으로 인정한다. */
    private static AmountResolution resolveExpectedAmount(BigDecimal raw) {
        AmountResolution resolution = resolveAmount(raw);
        return resolution.amount() == null || resolution.amount() <= 0
                ? AmountResolution.insufficient()
                : resolution;
    }

    private static DateResolution resolveDate(LocalDate date) {
        return date == null ? DateResolution.insufficient() : DateResolution.estimated(date);
    }

    private record InsuranceGroupKey(Long accountId, String normalizedKey, BigDecimal amount) {
    }

    private record AmountResolution(Long amount, String status) {
        static AmountResolution insufficient() {
            return new AmountResolution(null, STATUS_INSUFFICIENT);
        }

        static AmountResolution estimated(long amount) {
            return new AmountResolution(amount, STATUS_ESTIMATED);
        }
    }

    private record DateResolution(LocalDate date, String status) {
        static DateResolution insufficient() {
            return new DateResolution(null, STATUS_INSUFFICIENT);
        }

        static DateResolution estimated(LocalDate date) {
            return new DateResolution(date, STATUS_ESTIMATED);
        }
    }
}
