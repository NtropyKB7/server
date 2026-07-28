package com.ntropy.work.mapper;

import java.util.List;

import com.ntropy.work.domain.entity.JobSchedule;

public interface JobScheduleMapper {

    void insert(JobSchedule jobSchedule);

    JobSchedule findById(Long scheduleId);

    List<JobSchedule> findByJobId(Long jobId);

    void update(JobSchedule jobSchedule);

    void deleteById(Long scheduleId);
}
