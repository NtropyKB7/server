package com.ntropy.bff.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.bff.util.JsonNamingConverter;
import com.ntropy.common.dto.ai.AiReportSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프론트엔드에 반환할 AI 리포트 상세 응답 DTO입니다.
 *
 * <p>
 * ai-service에서 전달받은 JSON을 프론트엔드 형식으로 변환합니다.
 * FastAPI의 snake_case는 camelCase로 변환합니다.
 * </p>
 */
@Getter
@NoArgsConstructor
public class AiReportResponse {

    /**
     * AI_REPORT의 리포트 ID입니다.
     */
    private Long reportId;

    /**
     * 리포트 대상 연월입니다.
     */
    private String yearMonth;

    /**
     * 총소득, 총소비, 카테고리별 소비 등이 담긴 JSON입니다.
     */
    private JsonNode financialSummary;

    /**
     * 추천 상품과 추천 사유가 담긴 JSON입니다.
     */
    private JsonNode recommendation;

    /**
     * AI 리포트 생성 시각입니다.
     */
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss"
    )
    private LocalDateTime createdAt;

    /**
     * ai-service의 공통 DTO를
     * 프론트엔드 응답 DTO로 변환합니다.
     *
     * @param summary ai-service에서 전달받은 리포트
     * @return 프론트엔드 응답 객체
     */
    public static AiReportResponse from(
            AiReportSummary summary
    ) {
        AiReportResponse response =
                new AiReportResponse();

        response.reportId =
                summary.reportId();

        response.yearMonth =
                summary.yearMonth();

        /*
         * 재무 요약 JSON도 과거 snake_case 데이터가 있을 수 있으므로
         * camelCase로 변환합니다.
         */
        response.financialSummary =
                JsonNamingConverter.toCamelCase(
                        summary.financialSummary()
                );

        /*
         * FastAPI 추천 응답은 snake_case이므로
         * 프론트엔드 응답 전에 camelCase로 변환합니다.
         */
        response.recommendation =
                JsonNamingConverter.toCamelCase(
                        summary.recommendation()
                );

        response.createdAt =
                summary.createdAt();

        return response;
    }
}