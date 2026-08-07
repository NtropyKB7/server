package com.ntropy.bff.controller.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.ntropy.bff.dto.ai.AiReportResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.AiReportQueryClient;
import com.ntropy.common.dto.ai.AiReportSummary;

import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

/**
 * 프론트엔드의 AI 월간 리포트 조회 요청을 처리하는 BFF Controller입니다.
 *
 * 인증된 사용자 ID를 기준으로
 * ai-service에 AI 리포트 조회를 요청하고 프론트 응답 형태로 변환합니다.
 */
@RestController
@RequestMapping("/api/ai-reports")
@RequiredArgsConstructor
public class AiReportController {

    // ai-service가 제공하는 AI 리포트 조회 인터페이스입니다.
    private final AiReportQueryClient aiReportQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    /**
     * 특정 월의 AI 리포트를 조회합니다.
     *
     * 요청 예시:
     * GET /api/ai-reports/2026-07
     *
     * @param authentication 인증 정보. 사용자 ID를 추출하는 데 사용합니다.
     * @param yearMonth 조회할 리포트 대상 연월. 예: "2026-07"
     * @return 공통 응답 형식으로 감싼 AI 리포트 상세 데이터
     */
    @GetMapping("/{yearMonth}")
    public ResponseEntity<ApiResponse<AiReportResponse>> getAiReport(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable String yearMonth
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);

        // BFF는 인터페이스를 통해 ai-service에 리포트 조회를 요청합니다.
        AiReportSummary summary = aiReportQueryClient.findByUserIdAndYearMonth(
                userId,
                yearMonth
        );

        // 내부 공통 DTO를 프론트엔드 전용 DTO로 변환합니다.
        AiReportResponse response = AiReportResponse.from(summary);

        // 기존 BFF 공통 응답 형식으로 반환합니다.
        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK.value(),
                        "AI 리포트 조회에 성공했습니다.",
                        response
                )
        );
    }
}