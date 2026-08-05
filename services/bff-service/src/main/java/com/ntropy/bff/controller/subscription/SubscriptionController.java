package com.ntropy.bff.controller.subscription;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.subscription.request.PaymentMethodUpdateRequest;
import com.ntropy.bff.dto.subscription.request.SubscriptionInitRequest;
import com.ntropy.bff.dto.subscription.response.*;
import com.ntropy.common.client.SubscriptionCommandClient;
import com.ntropy.common.client.SubscriptionQueryClient;
import com.ntropy.common.dto.payment.PlanSummary;
import com.ntropy.common.dto.payment.PaymentConfigSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.Duration;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

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

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<PaymentConfigSummary>> getPaymentConfig() {
        PaymentConfigSummary config = subscriptionQueryClient.getPaymentConfig();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(ApiResponse.success(config));
    }

    @GetMapping
    public ApiResponse<SubscriptionResponse> getMySubscription(
            @RequestParam Long userId // TODO: AUTH 연동 후 인증 사용자 ID 사용
    ) {
        SubscriptionResponse response = SubscriptionResponse.from(subscriptionQueryClient.getMySubscription(userId));
        return ApiResponse.success(response);
    }


    //최초결제 + 빌링키 발급요청 (SUBSCRIPTION02_F01).

    @PostMapping
    public ApiResponse<SubscriptionResponse> initSubscription(
            @RequestParam Long userId,
            @RequestBody SubscriptionInitRequest request
    ) {
        SubscriptionResponse response = SubscriptionResponse.from(
                subscriptionCommandClient.initSubscription(userId, request.getBillingKey())
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/payment-method")
    public ApiResponse<SubscriptionResponse> updatePaymentMethod(
            @RequestParam Long userId,
            @RequestBody PaymentMethodUpdateRequest request
    ) {
        SubscriptionResponse response = SubscriptionResponse.from(
                subscriptionCommandClient.updatePaymentMethod(userId, request.getBillingKey())
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @RequestHeader("webhook-signature") String webhookSignature,
            @RequestBody String rawBody
    ) {
        boolean verified = subscriptionCommandClient.receiveWebhook(webhookId, webhookTimestamp, webhookSignature, rawBody);
        return verified ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/cancel")
    public ApiResponse<SubscriptionResponse> cancelSubscription(
            @RequestParam Long userId
    ) {
        SubscriptionResponse response = SubscriptionResponse.from(subscriptionCommandClient.cancelSubscription(userId));
        return ApiResponse.success(response);
    }

    @DeleteMapping("/cancel")
    public ApiResponse<SubscriptionResponse> revokeCancel(
            @RequestParam Long userId
    ) {
        SubscriptionResponse response = SubscriptionResponse.from(subscriptionCommandClient.revokeCancel(userId));
        return ApiResponse.success(response);
    }

    @GetMapping("/payments")
    public ApiResponse<PaymentHistoryResponse> getPaymentHistory(
            @RequestParam Long userId
    ) {
        PaymentHistoryResponse response = new PaymentHistoryResponse(
                subscriptionQueryClient.getPaymentHistory(userId).stream()
                        .map(PaymentHistoryItemResponse::from)
                        .collect(Collectors.toList())
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/management")
    public ApiResponse<SubscriptionManagementResponse> getSubscriptionManagement(
            @RequestParam Long userId // TODO: AUTH 연동 후 인증 사용자 ID 사용
    ) {
        SubscriptionResponse currentSubscription = SubscriptionResponse.from(subscriptionQueryClient.getMySubscription(userId));
        List<PlanSummary> availablePlans = subscriptionQueryClient.getPlans();

        return ApiResponse.success(new SubscriptionManagementResponse(currentSubscription, availablePlans));
    }
}
