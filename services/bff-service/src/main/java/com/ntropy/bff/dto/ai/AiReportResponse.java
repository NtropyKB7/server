package com.ntropy.bff.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.common.dto.ai.AiReportSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프론트엔드에 반환할 AI 리포트 상세 응답 DTO입니다.
 *
 * ai-service에서 전달한 AiReportSummary를 프론트 화면에 필요한 형태로 변환합니다.
 * 내부 조회용 사용자 ID는 프론트에 노출하지 않습니다.
 */
@Getter
@NoArgsConstructor
public class AiReportResponse {

    // AI_REPORT.report_id
    private Long reportId;

    // 리포트 대상 연월. 예: "2026-07"
    private String yearMonth;

    // 총수입, 총지출, 가용자금, 주요 소비 카테고리 등이 담긴 JSON 객체
    private JsonNode financialSummary;

    // 추천 상품과 추천 근거(reasoning)가 담긴 JSON 객체
    private JsonNode recommendation;

    // AI 리포트 생성 시각
    private LocalDateTime createdAt;

    /**
     * ai-service의 공통 DTO를 프론트엔드 응답 DTO로 변환합니다.
     *
     * @param summary ai-service에서 조회한 AI 리포트 데이터
     * @return 프론트엔드에 노출할 AI 리포트 응답 객체
     */
    public static AiReportResponse from(AiReportSummary summary) {
        AiReportResponse response = new AiReportResponse();

        response.reportId = summary.reportId();
        response.yearMonth = summary.yearMonth();
        response.financialSummary = summary.financialSummary();
        response.recommendation = summary.recommendation();
        response.createdAt = summary.createdAt();

        return response;
    }
}