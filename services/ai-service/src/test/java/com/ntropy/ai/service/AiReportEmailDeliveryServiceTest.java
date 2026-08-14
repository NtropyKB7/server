package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.ai.email.EmailMessage;
import com.ntropy.ai.email.EmailSender;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.common.client.AiReportQueryClient;
import com.ntropy.common.client.SubscriptionQueryClient;
import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.domain.Feature;
import com.ntropy.common.dto.ai.AiReportEmailDeliverySummary;
import com.ntropy.common.dto.ai.AiReportSummary;
import com.ntropy.common.dto.user.UserSummary;
import com.ntropy.common.exception.ServiceException;

class AiReportEmailDeliveryServiceTest {

    private SubscriptionQueryClient subscriptionClient;
    private UserQueryClient userClient;
    private AiReportQueryClient reportClient;
    private AiReportPdfService pdfService;
    private EmailSender emailSender;
    private AiReportEmailDeliveryService service;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionClient = mock(SubscriptionQueryClient.class);
        userClient = mock(UserQueryClient.class);
        reportClient = mock(AiReportQueryClient.class);
        pdfService = mock(AiReportPdfService.class);
        emailSender = mock(EmailSender.class);
        service = new AiReportEmailDeliveryService(
                subscriptionClient, userClient, reportClient, pdfService, emailSender
        );

        ObjectMapper mapper = new ObjectMapper();
        when(subscriptionClient.supportsFeature(7L, Feature.AI_REPORT)).thenReturn(true);
        when(userClient.getUserSummary(7L)).thenReturn(
                new UserSummary(7L, "사용자", "billing.owner@example.com", "GOOGLE", true, true, true)
        );
        when(reportClient.findByUserIdAndYearMonth(7L, "2026-05")).thenReturn(
                new AiReportSummary(31L, 7L, "2026-05", mapper.readTree("{\"total_income\":10}"),
                        mapper.readTree("{}"), LocalDateTime.of(2026, 6, 1, 1, 2))
        );
        when(pdfService.generate(any())).thenReturn(new byte[] {1, 2, 3});
    }

    @Test
    void deliversOnlyToServerResolvedEmailAndReturnsMaskedRecipient() {
        AiReportEmailDeliverySummary result = service.deliver(7L, "2026-05");

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        EmailMessage message = captor.getValue();
        assertEquals("billing.owner@example.com", message.recipient());
        assertEquals("[Ntropy] 2026년 5월 AI 재무 리포트", message.subject());
        assertEquals("Ntropy_AI_Report_2026-05.pdf", message.attachmentName());
        assertArrayEquals(new byte[] {1, 2, 3}, message.attachment());
        assertEquals("2026-05", result.yearMonth());
        assertEquals("EMAIL", result.channel());
        assertEquals("bi***@example.com", result.recipientEmail());
    }

    @Test
    void rejectsUserWithoutAiReportFeatureBeforeReadingSensitiveData() {
        when(subscriptionClient.supportsFeature(7L, Feature.AI_REPORT)).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.deliver(7L, "2026-05"));

        assertEquals(AiReportErrorCode.EMAIL_DELIVERY_FORBIDDEN.getStatusCode(), exception.getStatusCode());
        verify(userClient, never()).getUserSummary(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    void rejectsMissingEmailWithoutLookingUpReport() {
        when(userClient.getUserSummary(7L)).thenReturn(
                new UserSummary(7L, "사용자", "  ", "KAKAO", true, true, true)
        );

        ServiceException exception = assertThrows(ServiceException.class, () -> service.deliver(7L, "2026-05"));

        assertEquals(AiReportErrorCode.EMAIL_NOT_AVAILABLE.getStatusCode(), exception.getStatusCode());
        verify(reportClient, never()).findByUserIdAndYearMonth(any(), any());
    }

    @Test
    void rejectsInvalidYearMonthBeforeAnyDomainLookup() {
        ServiceException exception = assertThrows(ServiceException.class, () -> service.deliver(7L, "2026-13"));

        assertEquals(400, exception.getStatusCode());
        verify(subscriptionClient, never()).supportsFeature(any(), any());
    }
}
