package com.ntropy.bff.controller.subscription;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.subscription.request.SubscriptionInitRequest;
import com.ntropy.bff.dto.subscription.response.PlansResponse;
import com.ntropy.bff.dto.subscription.response.SubscriptionResponse;
import com.ntropy.common.client.SubscriptionCommandClient;
import com.ntropy.common.client.SubscriptionQueryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구독/결제 관련 프론트 노출 엔드포인트.
 * 실제 로직은 SubscriptionQueryClient/SubscriptionCommandClient(payment-service의
 * LocalSubscriptionQueryClient)에 위임하고, 이 컨트롤러는 응답 모양만 조립한다.
 *
 * 이 컨트롤러의 모든 엔드포인트는 Authorization: accessToken 헤더가 필요하다
 * (Spring Security 인증 필터가 처리 - AUTH 도메인 담당).
 *
 * ⚠️ initSubscription()에서 SubscriptionService가 던지는 ServiceException은
 * 여기서 안 잡는다 - bff-service의 GlobalExceptionHandler(@RestControllerAdvice)가
 * 전역으로 잡아서 ApiResponse.fail(...)로 변환한다. 단, ServletConfig의
 * @ComponentScan에 ControllerAdvice.class 필터가 추가돼 있어야 실제로 동작한다
 * (아직 안 됐으면 이번 기회에 반영 필요 - GlobalExceptionHandler 클래스 주석 참고).
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    /**
     * ⚠️⚠️ 임시 코드 (AUTH 필터 연동 전까지만) ⚠️⚠️
     * AUTH 미연동 상태를 알려주는 디버그용 힌트. ErrorCode에 영구히 넣기엔
     * "AUTH 미연동"이라는 개발 중 임시 상황 설명이라 여기 로컬 상수로만 둔다.
     * AUTH 필터 연동되면 이 상수와 아래 fallback 로직을 통째로 지운다.
     */
    private static final String AUTH_NOT_WIRED_HINT =
            "(AUTH 미연동 상태 - 임시로 ?userId= 파라미터를 넘겨서 테스트하세요)";

    private final SubscriptionQueryClient subscriptionQueryClient;
    private final SubscriptionCommandClient subscriptionCommandClient;

    @Autowired
    public SubscriptionController(SubscriptionQueryClient subscriptionQueryClient,
                                  SubscriptionCommandClient subscriptionCommandClient) {
        this.subscriptionQueryClient = subscriptionQueryClient;
        this.subscriptionCommandClient = subscriptionCommandClient;
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
        Long resolvedUserId = resolveUserId(authentication, userId);
        if (resolvedUserId == null) {
            return ApiResponse.fail(ErrorCode.UNAUTHORIZED, AUTH_NOT_WIRED_HINT);
        }

        SubscriptionResponse response = SubscriptionResponse.from(subscriptionQueryClient.getMySubscription(resolvedUserId));
        return ApiResponse.success(response);
    }

    /**
     * 최초결제 + 빌링키 발급요청 (SUBSCRIPTION02_F01).
     * 실제 검증(포트원 서버 조회, 금액/주문번호 대조)은 SubscriptionService에서 한다 -
     * 여기는 요청을 받아서 넘기고 응답을 감싸는 역할만 한다.
     */
    @PostMapping
    public ApiResponse<SubscriptionResponse> initSubscription(
            Authentication authentication,
            @RequestParam(required = false) Long userId, // TODO: AUTH 연동되면 제거
            @RequestBody SubscriptionInitRequest request
    ) {
        Long resolvedUserId = resolveUserId(authentication, userId);
        if (resolvedUserId == null) {
            return ApiResponse.fail(ErrorCode.UNAUTHORIZED, AUTH_NOT_WIRED_HINT);
        }

        SubscriptionResponse response = SubscriptionResponse.from(subscriptionCommandClient.initSubscription(
                resolvedUserId,
                request.getPaymentId(),
                request.getCustomerUid(),
                request.getAmount()
        ));
        return ApiResponse.success(response);
    }

    /** ⚠️ 임시: AUTH 연동되면 파라미터 하나(Authentication)만 남기고 이 메서드 내용 단순화 */
    private Long resolveUserId(Authentication authentication, Long userId) {
        return authentication != null ? Long.valueOf(authentication.getName()) : userId;
    }
}