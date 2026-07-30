package com.ntropy.bff.controller.work;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.work.request.WorkLogPatchRequest;
import com.ntropy.bff.dto.work.request.WorkLogRegisterRequest;
import com.ntropy.bff.dto.work.response.WorkLogCreateResponse;
import com.ntropy.common.client.WorkLogCommandClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogCommandClient workLogCommandClient;

    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<WorkLogCreateResponse>> registerPlan(@RequestBody WorkLogRegisterRequest request) {
        Long workId = workLogCommandClient.registerPlan(request.toCommand());
        ApiResponse<WorkLogCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "근무 계획이 등록되었습니다.", new WorkLogCreateResponse(workId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/actual")
    public ResponseEntity<ApiResponse<WorkLogCreateResponse>> registerActual(@RequestBody WorkLogRegisterRequest request) {
        Long workId = workLogCommandClient.registerActual(request.toCommand());
        ApiResponse<WorkLogCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "근무일지가 등록되었습니다.", new WorkLogCreateResponse(workId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{workId}/edit")
    public ApiResponse<Void> editWorkLog(@PathVariable Long workId, @RequestBody WorkLogPatchRequest request) {
        workLogCommandClient.editWorkLog(workId, request.toCommand());
        return ApiResponse.success(HttpStatus.OK.value(), "근무일지가 수정되었습니다.", null);
    }

    @PatchMapping("/{workId}/confirm")
    public ApiResponse<Void> confirmWorkLog(@PathVariable Long workId, @RequestBody WorkLogPatchRequest request) {
        workLogCommandClient.confirmWorkLog(workId, request.toCommand());
        return ApiResponse.success(HttpStatus.OK.value(), "근무일지가 확정되었습니다.", null);
    }

    @DeleteMapping("/{workId}")
    public ApiResponse<Void> deleteWorkLog(@PathVariable Long workId) {
        workLogCommandClient.deleteWorkLog(workId);
        return ApiResponse.success(HttpStatus.OK.value(), "근무일지가 삭제되었습니다.", null);
    }
}
