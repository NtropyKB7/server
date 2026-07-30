package com.ntropy.bff.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.JobResponse;
import com.ntropy.bff.dto.work.JobsResponse;
import com.ntropy.common.client.JobQueryClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobQueryClient jobQueryClient;

    @GetMapping("/{jobId}")
    public JobResponse getJob(@PathVariable Long jobId) {
        return JobResponse.from(jobQueryClient.getJob(jobId));
    }

    @GetMapping
    public JobsResponse getJobs(@RequestParam Long userId) {
        return JobsResponse.from(jobQueryClient.getJobsByUserId(userId));
    }
}
