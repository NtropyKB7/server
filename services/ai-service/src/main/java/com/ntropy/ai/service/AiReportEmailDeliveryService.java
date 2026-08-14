package com.ntropy.ai.service;

import java.time.DateTimeException;
import java.time.YearMonth;

import org.springframework.stereotype.Service;

import com.ntropy.ai.email.EmailMessage;
import com.ntropy.ai.email.EmailSender;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.common.client.AiReportQueryClient;
import com.ntropy.common.client.SubscriptionQueryClient;
import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.domain.Feature;
import com.ntropy.common.dto.ai.AiReportDetailSummary;
import com.ntropy.common.dto.ai.AiReportEmailDeliverySummary;
import com.ntropy.common.dto.ai.AiReportSummary;
import com.ntropy.common.dto.user.UserSummary;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 구독·사용자·리포트 검증부터 PDF 첨부 발송까지 수동 전달 흐름을 조정한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportEmailDeliveryService {

    private final SubscriptionQueryClient subscriptionQueryClient;
    private final UserQueryClient userQueryClient;
    private final AiReportQueryClient aiReportQueryClient;
    private final AiReportPdfService aiReportPdfService;
    private final EmailSender emailSender;

    public AiReportEmailDeliverySummary deliver(Long userId, String requestedYearMonth) {
        String yearMonth = validateYearMonth(requestedYearMonth);
        Long reportId = null;
        try {
            if (!subscriptionQueryClient.supportsFeature(userId, Feature.AI_REPORT)) {
                throw new ServiceException(AiReportErrorCode.EMAIL_DELIVERY_FORBIDDEN);
            }

            UserSummary user = userQueryClient.getUserSummary(userId);
            String recipient = user == null ? null : user.email();
            if (recipient == null || recipient.isBlank()) {
                throw new ServiceException(AiReportErrorCode.EMAIL_NOT_AVAILABLE);
            }

            AiReportSummary summary = aiReportQueryClient.findByUserIdAndYearMonth(userId, yearMonth);
            reportId = summary.reportId();
            AiReportDetailSummary detail = AiReportDetailSummary.from(summary);
            byte[] pdf = aiReportPdfService.generate(detail);

            emailSender.send(new EmailMessage(
                    recipient,
                    subject(YearMonth.parse(yearMonth)),
                    body(YearMonth.parse(yearMonth)),
                    "Ntropy_AI_Report_" + yearMonth + ".pdf",
                    "application/pdf",
                    pdf
            ));

            log.info("AI 리포트 전달 완료. userId={}, reportId={}, yearMonth={}, channel=EMAIL, result=SUCCESS",
                    userId, reportId, yearMonth);
            return new AiReportEmailDeliverySummary(yearMonth, "EMAIL", maskEmail(recipient));
        } catch (RuntimeException exception) {
            log.error("AI 리포트 전달 실패. userId={}, reportId={}, yearMonth={}, channel=EMAIL, result=FAILED, failureCode={}",
                    userId, reportId, yearMonth, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private static String validateYearMonth(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(AiReportErrorCode.INVALID_REQUEST);
        }
        try {
            YearMonth parsed = YearMonth.parse(value);
            if (!parsed.toString().equals(value)) {
                throw new ServiceException(AiReportErrorCode.INVALID_REQUEST);
            }
            return value;
        } catch (DateTimeException exception) {
            throw new ServiceException(AiReportErrorCode.INVALID_REQUEST);
        }
    }

    private static String subject(YearMonth yearMonth) {
        return String.format("[Ntropy] %d년 %d월 AI 재무 리포트", yearMonth.getYear(), yearMonth.getMonthValue());
    }

    private static String body(YearMonth yearMonth) {
        return String.format(
                "안녕하세요.%n%n%d년 %d월 Ntropy AI 재무 리포트를 첨부했습니다.%n"
                        + "자세한 소비 분석과 맞춤 금융상품 추천을 PDF에서 확인해 주세요.%n%n"
                        + "감사합니다.%nNtropy 드림",
                yearMonth.getYear(), yearMonth.getMonthValue()
        );
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }
}
