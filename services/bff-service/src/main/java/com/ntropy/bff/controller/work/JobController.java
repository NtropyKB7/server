package com.ntropy.bff.controller.work;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.request.JobCreateRequest;
import com.ntropy.bff.dto.work.response.JobCreateResponse;
import com.ntropy.bff.dto.work.response.JobResponse;
import com.ntropy.bff.dto.work.request.JobUpdateRequest;
import com.ntropy.bff.dto.work.response.JobsResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.common.client.JobCommandClient;
import com.ntropy.common.client.JobQueryClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobQueryClient jobQueryClient;
    private final JobCommandClient jobCommandClient;

    @GetMapping("/{jobId}")
    public ApiResponse<JobResponse> getJob(@PathVariable Long jobId) {
        return ApiResponse.success(JobResponse.from(jobQueryClient.getJob(jobId)));
    }

    @GetMapping
    public ApiResponse<JobsResponse> getJobs(@RequestParam Long userId) {
        return ApiResponse.success(JobsResponse.from(jobQueryClient.getJobsByUserId(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobCreateResponse>> createJob(@RequestBody JobCreateRequest request) {
        Long jobId = jobCommandClient.registerJob(request.toCommand());
        ApiResponse<JobCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "잡이 등록되었습니다.", new JobCreateResponse(jobId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{jobId}")
    public ApiResponse<Void> updateJob(@PathVariable Long jobId, @RequestBody JobUpdateRequest request) {
        jobCommandClient.updateJob(jobId, request.toCommand());
        return ApiResponse.success(HttpStatus.OK.value(), "잡이 수정되었습니다.", null);
    }

    @PatchMapping("/{jobId}/deactivate")
    public ApiResponse<Void> deactivateJob(@PathVariable Long jobId) {
        jobCommandClient.deactivateJob(jobId);
        return ApiResponse.success(HttpStatus.OK.value(), "잡이 비활성화되었습니다.", null);
    }
}
