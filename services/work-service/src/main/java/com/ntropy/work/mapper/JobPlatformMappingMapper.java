package com.ntropy.work.mapper;

import java.util.List;

import com.ntropy.work.domain.entity.JobPlatformMapping;

public interface JobPlatformMappingMapper {

    void insert(JobPlatformMapping jobPlatformMapping);

    JobPlatformMapping findById(Long mappingId);

    List<JobPlatformMapping> findByJobId(Long jobId);

    void update(JobPlatformMapping jobPlatformMapping);

    void deleteById(Long mappingId);
}
