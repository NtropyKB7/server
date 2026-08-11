package com.ntropy.common.client;

import com.ntropy.common.dto.account.FinancialPositionSummary;

/**
 * diagnosis-service가 재무진단에 사용하는 사용자 금융자산·부채 집계 조회 계약.
 *
 * <p>account-service에 이미 동기화된 ACCOUNT 데이터만으로 계산하며 CODEF API를
 * 다시 호출하지 않는다. 집계 대상 계좌의 잔액이 불완전하면(소수부, null, 음수,
 * Long 변환·합산 overflow) 예외를 던져 호출자가 진단 결과 갱신을 중단하도록 한다.</p>
 */
public interface FinancialPositionQueryClient {

    FinancialPositionSummary findFinancialPosition(Long userId);
}
