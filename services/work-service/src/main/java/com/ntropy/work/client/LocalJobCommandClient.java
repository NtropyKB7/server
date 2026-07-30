package com.ntropy.work.client;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.JobCommandClient;
import com.ntropy.common.dto.work.command.JobRegisterCommand;
import com.ntropy.common.dto.work.command.JobScheduleCommand;
import com.ntropy.common.dto.work.command.JobUpdateCommand;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobSchedule;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.service.JobPlatformMappingService;
import com.ntropy.work.service.JobService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalJobCommandClient implements JobCommandClient {

    private final JobService jobService;
    private final JobPlatformMappingService jobPlatformMappingService;

    @Override
    public Long registerJob(JobRegisterCommand command) {
        Job job = Job.builder()
                .userId(command.getUserId())
                .categoryId(command.getCategoryId())
                .jobName(command.getJobName())
                .settlementType(SettlementType.valueOf(command.getSettlementType()))
                .hourlyWage(command.getHourlyWage())
                .monthlyWage(command.getMonthlyWage())
                .perTaskWage(command.getPerTaskWage())
                .taskPerHour(command.getTaskPerHour())
                .isRegular(command.getIsRegular())
                .baseFatigue(command.getBaseFatigue())
                .build();

        List<JobSchedule> schedules = toSchedules(command.getSchedules());

        jobService.registerJob(job, schedules);

        for (Long platformId : safe(command.getPlatformIds())) {
            jobPlatformMappingService.register(job.getJobId(), platformId);
        }

        return job.getJobId();
    }

    @Override
    public void updateJob(Long jobId, JobUpdateCommand command) {
        Job job = Job.builder()
                .jobId(jobId)
                .categoryId(command.getCategoryId())
                .jobName(command.getJobName())
                .settlementType(SettlementType.valueOf(command.getSettlementType()))
                .hourlyWage(command.getHourlyWage())
                .monthlyWage(command.getMonthlyWage())
                .perTaskWage(command.getPerTaskWage())
                .taskPerHour(command.getTaskPerHour())
                .isRegular(command.getIsRegular())
                .baseFatigue(command.getBaseFatigue())
                .build();

        jobService.updateJob(job);
    }

    @Override
    public void deactivateJob(Long jobId) {
        jobService.deactivateJob(jobId);
    }

    private List<JobSchedule> toSchedules(List<JobScheduleCommand> commands) {
        if (commands == null) {
            return Collections.emptyList();
        }
        return commands.stream()
                .map(c -> JobSchedule.builder()
                        .dayOfWeek(c.getDayOfWeek())
                        .startTime(c.getStartTime())
                        .endTime(c.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }

    private List<Long> safe(List<Long> ids) {
        return ids == null ? Collections.emptyList() : ids;
    }
}
