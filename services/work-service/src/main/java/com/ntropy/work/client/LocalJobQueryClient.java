package com.ntropy.work.client;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.JobQueryClient;
import com.ntropy.common.dto.work.summary.JobScheduleSummary;
import com.ntropy.common.dto.work.summary.JobSummary;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobSchedule;
import com.ntropy.work.service.JobService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalJobQueryClient implements JobQueryClient {

    private final JobService jobService;

    @Override
    public JobSummary getJob(Long jobId) {
        return toSummary(jobService.findById(jobId));
    }

    @Override
    public List<JobSummary> getJobsByUserId(Long userId) {
        return jobService.findByUserId(userId).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    private JobSummary toSummary(Job job) {
        return JobSummary.builder()
                .jobId(job.getJobId())
                .userId(job.getUserId())
                .categoryId(job.getCategoryId())
                .jobName(job.getJobName())
                .settlementType(job.getSettlementType().name())
                .hourlyWage(job.getHourlyWage())
                .monthlyWage(job.getMonthlyWage())
                .perTaskWage(job.getPerTaskWage())
                .taskPerHour(job.getTaskPerHour())
                .isRegular(job.getIsRegular())
                .baseFatigue(job.getBaseFatigue())
                .isActive(job.getIsActive())
                .schedules(toScheduleSummaries(jobService.findSchedulesByJobId(job.getJobId())))
                .build();
    }

    private List<JobScheduleSummary> toScheduleSummaries(List<JobSchedule> schedules) {
        return schedules.stream()
                .map(s -> JobScheduleSummary.builder()
                        .dayOfWeek(s.getDayOfWeek())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }
}
