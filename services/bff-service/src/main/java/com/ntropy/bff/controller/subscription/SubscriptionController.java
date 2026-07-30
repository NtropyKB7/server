package com.ntropy.bff.controller.subscription;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.subscription.request.PaymentMethodUpdateRequest;
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

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    // 임시 Auth 코드
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


    //최초결제 + 빌링키 발급요청 (SUBSCRIPTION02_F01).

    @PostMapping
    public ApiResponse<SubscriptionResponse> initSubscription(
            Authentication authentication,
            @RequestParam(required = false) Long userId,
            @RequestBody SubscriptionInitRequest request
    ) {
        Long resolvedUserId = resolveUserId(authentication, userId);
        if (resolvedUserId == null) {
            return ApiResponse.fail(ErrorCode.UNAUTHORIZED, AUTH_NOT_WIRED_HINT);
        }

        SubscriptionResponse response = SubscriptionResponse.from(
                subscriptionCommandClient.initSubscription(resolvedUserId, request.getBillingKey())
        );
        return ApiResponse.success(response);
    }


    private Long resolveUserId(Authentication authentication, Long userId) {
        return authentication != null ? Long.valueOf(authentication.getName()) : userId;
    }

    @PostMapping("/payment-method")
    public ApiResponse<SubscriptionResponse> updatePaymentMethod(
            Authentication authentication,
            @RequestParam(required = false) Long userId,
            @RequestBody PaymentMethodUpdateRequest request
    ) {
        Long resolvedUserId = resolveUserId(authentication, userId);
        if (resolvedUserId == null) {
            return ApiResponse.fail(ErrorCode.UNAUTHORIZED, AUTH_NOT_WIRED_HINT);
        }

        SubscriptionResponse response = SubscriptionResponse.from(
                subscriptionCommandClient.updatePaymentMethod(resolvedUserId, request.getBillingKey())
        );
        return ApiResponse.success(response);
    }
}