package com.ntropy.work.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobSchedule;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.JobScheduleMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobMapper jobMapper;
    private final JobScheduleMapper jobScheduleMapper;
    private final CategoryService categoryService;

    /**
     * 잡 등록. 정기근무 스케줄이 있으면 같이 등록한다(둘 다 성공하거나 둘 다 롤백).
     *
     * @param job       jobId/createdAt/updatedAt/isActive는 이 메서드가 채우므로 호출부에서 안 넣어도 됨
     * @param schedules 정기근무 아니면 null 또는 빈 리스트
     */
    @Transactional
    public Job registerJob(Job job, List<JobSchedule> schedules) {
        validate(job);
        validateScheduleConsistency(job, schedules);
        categoryService.findById(job.getCategoryId());

        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        if (job.getIsActive() == null) {
            job.setIsActive(true);
        }

        jobMapper.insert(job);

        for (JobSchedule schedule : safe(schedules)) {
            schedule.setJobId(job.getJobId());
            jobScheduleMapper.insert(schedule);
        }

        return job;
    }

    public Job findById(Long jobId) {
        Job job = jobMapper.findById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("존재하지 않는 잡입니다. jobId=" + jobId);
        }
        return job;
    }

    public List<Job> findByUserId(Long userId) {
        return jobMapper.findByUserId(userId);
    }

    @Transactional
    public Job updateJob(Job job) {
        findById(job.getJobId());
        validate(job);
        categoryService.findById(job.getCategoryId());

        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        return job;
    }

    @Transactional
    public void deactivateJob(Long jobId) {
        Job job = findById(jobId);
        job.setIsActive(false);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
    }

    private void validate(Job job) {
        if (!StringUtils.hasText(job.getJobName())) {
            throw new IllegalArgumentException("job_name은 필수입니다.");
        }
        if (job.getCategoryId() == null) {
            throw new IllegalArgumentException("category_id는 필수입니다.");
        }
        if (!StringUtils.hasText(job.getSettlementType())) {
            throw new IllegalArgumentException("settlement_type은 필수입니다.");
        }
        if (job.getIsRegular() == null) {
            throw new IllegalArgumentException("is_regular는 필수입니다.");
        }
        if (job.getBaseFatigue() == null) {
            throw new IllegalArgumentException("base_fatigue는 필수입니다.");
        }
    }

    private List<JobSchedule> safe(List<JobSchedule> schedules) {
        return schedules == null ? Collections.emptyList() : schedules;
    }

    private void validateScheduleConsistency(Job job, List<JobSchedule> schedules) {
        boolean hasSchedules = !safe(schedules).isEmpty();
        if (Boolean.TRUE.equals(job.getIsRegular()) && !hasSchedules) {
            throw new IllegalArgumentException("정기잡(is_regular=true)은 정기근무 스케줄이 최소 1개 필요합니다.");
        }
        if (Boolean.FALSE.equals(job.getIsRegular()) && hasSchedules) {
            throw new IllegalArgumentException("비정기잡(is_regular=false)에는 정기근무 스케줄을 등록할 수 없습니다.");
        }
    }
}
