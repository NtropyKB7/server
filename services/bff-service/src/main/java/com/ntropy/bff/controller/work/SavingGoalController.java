package com.ntropy.bff.controller.work;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.work.request.SavingGoalCreateRequest;
import com.ntropy.bff.dto.work.response.SavingGoalCreateResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.SavingGoalCommandClient;
import com.ntropy.common.client.UserCommandClient;

import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/saving-goals")
@RequiredArgsConstructor
public class SavingGoalController {

    private final SavingGoalCommandClient savingGoalCommandClient;
    private final UserCommandClient userCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<SavingGoalCreateResponse>> createSavingGoal(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody SavingGoalCreateRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        Long savingGoalId = savingGoalCommandClient.registerSavingGoal(request.toCommand(userId));
        userCommandClient.completeOnboarding(userId);
        ApiResponse<SavingGoalCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "저축목표가 등록되었습니다.", new SavingGoalCreateResponse(savingGoalId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
