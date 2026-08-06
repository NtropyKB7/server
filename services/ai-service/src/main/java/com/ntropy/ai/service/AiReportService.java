package com.ntropy.ai.service;

import org.springframework.stereotype.Service;

import com.ntropy.ai.domain.AiReport;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.ai.mapper.AiReportMapper;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/**
 * AI_REPORT 조회와 관련된 비즈니스 로직을 담당하는 Service입니다.
 *
 * Controller나 다른 서비스 모듈은 Mapper를 직접 호출하지 않고
 * 반드시 이 Service를 통해 AI 리포트를 조회합니다.
 */
@Service
@RequiredArgsConstructor
public class AiReportService {

    // AI_REPORT 테이블에 접근하는 MyBatis Mapper입니다.
    private final AiReportMapper aiReportMapper;

    /**
     * 특정 사용자의 특정 월 AI 리포트를 조회합니다.
     *
     * @param userId 로그인한 사용자 ID
     * @param yearMonth 조회 대상 연월. 예: "2026-08"
     * @return 조회된 AI_REPORT Domain 객체
     */
    public AiReport findByUserIdAndYearMonth(
            Long userId,
            String yearMonth
    ) {
        // 잘못된 사용자 ID로 조회하는 요청을 미리 막습니다.
        if (userId == null || userId <= 0) {
            throw new ServiceException(
                    AiReportErrorCode.INVALID_REQUEST,
                    "userId는 양수여야 합니다."
            );
        }

        // 연월은 "2026-08"처럼 YYYY-MM 형식만 허용합니다.
        if (yearMonth == null || !yearMonth.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException(
                    AiReportErrorCode.INVALID_REQUEST,
                    "yearMonth는 YYYY-MM 형식이어야 합니다."
            );
        }

        // 기존 AiReportMapper의 조회 SQL을 그대로 사용합니다.
        AiReport aiReport = aiReportMapper.findByUserIdAndYearMonth(
                userId,
                yearMonth
        );

        // DB에 해당 월 리포트가 없으면 404 예외를 발생시킵니다.
        if (aiReport == null) {
            throw new ServiceException(AiReportErrorCode.REPORT_NOT_FOUND);
        }

        return aiReport;
    }
}