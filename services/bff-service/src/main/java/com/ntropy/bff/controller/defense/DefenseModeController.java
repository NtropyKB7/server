package com.ntropy.bff.controller.defense;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.defense.request.DefenseModeEnterRequest;
import com.ntropy.bff.dto.defense.request.DefenseModeReleaseRequest;
import com.ntropy.bff.dto.defense.response.DefenseCausesResponse;
import com.ntropy.bff.dto.defense.response.DefenseModeResponse;
import com.ntropy.common.client.DefenseModeCommandClient;
import com.ntropy.common.client.DefenseModeQueryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/defense")
@RequiredArgsConstructor
public class DefenseModeController {
    private final DefenseModeCommandClient defenseModeCommandClient;
    private final DefenseModeQueryClient defenseModeQueryClient;

    @GetMapping("/causes")
    public ApiResponse<DefenseCausesResponse> getCauses() {
        return ApiResponse.success(new DefenseCausesResponse(defenseModeQueryClient.getCauses()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DefenseModeResponse>> enter(@RequestBody DefenseModeEnterRequest request) {
        DefenseModeResponse response = DefenseModeResponse.from(defenseModeCommandClient.enter(request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(HttpStatus.CREATED.value(), "방어모드가 시작되었습니다.", response));
    }

    @GetMapping("/active")
    public ApiResponse<DefenseModeResponse> getCurrent(@RequestParam Long userId) {
        return ApiResponse.success(DefenseModeResponse.from(defenseModeQueryClient.getCurrent(userId)));
    }

    @PatchMapping("/{defenseId}/return")
    public ApiResponse<DefenseModeResponse> release(@PathVariable Long defenseId,
                                                     @RequestBody DefenseModeReleaseRequest request) {
        return ApiResponse.success(200, "방어모드가 해제되었습니다.",
                DefenseModeResponse.from(defenseModeCommandClient.release(defenseId, request.toCommand())));
    }
}
