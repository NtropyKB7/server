package com.ntropy.bff.controller.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.bff.exception.GlobalExceptionHandler;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.AiReportEmailDeliveryClient;
import com.ntropy.common.client.AiReportQueryClient;
import com.ntropy.common.dto.ai.AiReportEmailDeliverySummary;
import com.ntropy.common.dto.ai.AiReportSummary;

class AiReportControllerContractTest {

    private StubDeliveryClient deliveryClient;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deliveryClient = new StubDeliveryClient();
        objectMapper = new ObjectMapper();
        AiReportController controller = new AiReportController(
                new EmptyAiReportQueryClient(), deliveryClient, new AuthenticatedUserIdResolver()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesPostEmailDeliveryWithOnlyYearMonthRequestParameter() throws Exception {
        Method method = AiReportController.class.getDeclaredMethod(
                "deliverAiReportByEmail", Authentication.class, String.class
        );
        assertArrayEquals(new String[] {"/deliveries/email"}, method.getAnnotation(PostMapping.class).value());

        Parameter[] parameters = method.getParameters();
        assertEquals(2, parameters.length);
        assertFalse(parameters[0].isAnnotationPresent(RequestBody.class));
        RequestParam yearMonth = parameters[1].getAnnotation(RequestParam.class);
        assertEquals("", yearMonth.name());
        assertFalse(parameters[1].isAnnotationPresent(RequestBody.class));
    }

    @Test
    void usesAuthenticatedUserAndReturnsMaskedDeliveryResult() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai-reports/deliveries/email")
                        .param("yearMonth", "2026-05")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
        );
        assertEquals(42L, deliveryClient.userId);
        assertEquals("2026-05", deliveryClient.yearMonth);
        assertEquals("AI 리포트를 이메일로 전송했습니다.", body.path("message").asText());
        assertEquals("EMAIL", body.path("data").path("channel").asText());
        assertEquals("bi***@example.com", body.path("data").path("recipientEmail").asText());
    }

    @Test
    void missingYearMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/ai-reports/deliveries/email").principal(authentication()))
                .andExpect(status().isBadRequest());
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(42L, "unused", Collections.emptyList());
    }

    private static final class StubDeliveryClient implements AiReportEmailDeliveryClient {
        private Long userId;
        private String yearMonth;

        @Override
        public AiReportEmailDeliverySummary deliver(Long userId, String yearMonth) {
            this.userId = userId;
            this.yearMonth = yearMonth;
            return new AiReportEmailDeliverySummary(yearMonth, "EMAIL", "bi***@example.com");
        }
    }

    private static final class EmptyAiReportQueryClient implements AiReportQueryClient {
        @Override
        public AiReportSummary findByUserIdAndYearMonth(Long userId, String yearMonth) {
            return null;
        }

        @Override
        public List<AiReportSummary> findAllByUserId(Long userId) {
            return List.of();
        }
    }
}
