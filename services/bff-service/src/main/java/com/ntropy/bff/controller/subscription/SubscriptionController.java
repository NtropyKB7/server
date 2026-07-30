package com.ntropy.bff.controller.subscription;

import com.ntropy.bff.dto.subscription.PlansResponse;
import com.ntropy.bff.dto.subscription.SubscriptionResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.common.client.SubscriptionQueryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구독/결제 관련 프론트 노출 엔드포인트.
 * 실제 로직은 SubscriptionQueryClient(payment-service의 LocalSubscriptionQueryClient)에
 * 위임하고, 이 컨트롤러는 응답 모양만 조립한다.
 *
 * 이 컨트롤러의 모든 엔드포인트는 Authorization: accessToken 헤더가 필요하다
 * (Spring Security 인증 필터가 처리 - AUTH 도메인 담당).
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionQueryClient subscriptionQueryClient;

    @Autowired
    public SubscriptionController(SubscriptionQueryClient subscriptionQueryClient) {
        this.subscriptionQueryClient = subscriptionQueryClient;
    }

    @GetMapping("/plans")
    public ApiResponse<PlansResponse> getPlans() {
        PlansResponse response = new PlansResponse(subscriptionQueryClient.getPlans());
        return ApiResponse.success(response);
    }

    /**
     * ⚠️⚠️ 임시 코드 (AUTH 필터 연동 전까지만) ⚠️⚠️
     * 지금은 WebConfig에 Spring Security 필터체인이 등록돼 있지 않아서
     * Authentication이 항상 null로 들어온다. AUTH 필터가 실제로 연동되면
     * - userId 쿼리파라미터 지우고
     * - authentication == null 체크 없애고
     * - Long.valueOf(authentication.getName())만 남기면 된다.
     *
     * 유저 식별 방식 가정: Authentication.getName()이 회원 ID 문자열이라고 가정
     * (AUTH-010 "검증 성공 시 SecurityContext에 인증정보를 저장한다"를 근거로 함).
     */
    @GetMapping
    public ApiResponse<SubscriptionResponse> getMySubscription(
            Authentication authentication,
            @RequestParam(required = false) Long userId // TODO: AUTH 연동되면 제거
    ) {
        Long resolvedUserId = authentication != null
                ? Long.valueOf(authentication.getName())
                : userId;

        if (resolvedUserId == null) {
            return ApiResponse.fail(401, "인증 정보가 없습니다. (AUTH 미연동 상태 - 임시로 ?userId= 파라미터를 넘겨서 테스트하세요)");
        }

        SubscriptionResponse response = SubscriptionResponse.from(subscriptionQueryClient.getMySubscription(resolvedUserId));
        return ApiResponse.success(response);
    }
}