package com.ntropy.bff.dto.ai;

import java.time.LocalDateTime;

import com.ntropy.common.dto.ai.AiReportSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AI 리포트 목록 화면에서 리포트 한 건을 표시하기 위한 응답 DTO입니다.
 *
 * 목록 화면에는 상세 재무 JSON 전체를 전달하지 않고,
 * 리포트를 식별하고 선택하는 데 필요한 최소 정보만 전달합니다.
 */
@Getter
@AllArgsConstructor
public class AiReportListItemResponse {

    // AI 리포트 고유 ID입니다.
    private final Long reportId;

    // 리포트 대상 연월입니다. 예: "2026-07"
    private final String yearMonth;

    // 프론트엔드 목록 화면에 표시할 제목입니다. 예: "2026년 7월 리포트"
    private final String reportTitle;

    // AI 리포트 생성 시각입니다.
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss"
    )
    private final LocalDateTime createdAt;

    /**
     * ai-service에서 전달받은 공통 리포트 DTO를
     * 프론트엔드 목록 전용 DTO로 변환합니다.
     *
     * @param summary ai-service의 AI 리포트 요약 데이터
     * @return 프론트엔드 목록 화면에 전달할 리포트 항목
     */
    public static AiReportListItemResponse from(AiReportSummary summary) {
        return new AiReportListItemResponse(
                summary.reportId(),
                summary.yearMonth(),
                createReportTitle(summary.yearMonth()),
                summary.createdAt()
        );
    }

    /**
     * DB에 저장된 "YYYY-MM" 형식의 연월을
     * 화면 표시용 "YYYY년 M월 리포트" 형식으로 변환합니다.
     *
     * 예: "2026-07" -> "2026년 7월 리포트"
     *
     * @param yearMonth 리포트 대상 연월
     * @return 화면 표시용 리포트 제목
     */
    private static String createReportTitle(String yearMonth) {
        String[] yearMonthParts = yearMonth.split("-");

        int year = Integer.parseInt(yearMonthParts[0]);
        int month = Integer.parseInt(yearMonthParts[1]);

        return year + "년 " + month + "월 리포트";
    }
}