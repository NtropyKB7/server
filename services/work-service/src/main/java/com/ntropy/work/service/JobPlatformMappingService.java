package com.ntropy.work.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.work.domain.entity.JobPlatformMapping;
import com.ntropy.work.mapper.JobPlatformMappingMapper;
import com.ntropy.work.mapper.PlatformMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobPlatformMappingService {

    private final JobPlatformMappingMapper jobPlatformMappingMapper;
    private final PlatformMapper platformMapper;

    public List<JobPlatformMapping> findByJobId(Long jobId) {
        return jobPlatformMappingMapper.findByJobId(jobId);
    }

    public JobPlatformMapping register(Long jobId, Long platformId) {
        if (platformMapper.findById(platformId) == null) {
            throw new IllegalArgumentException("존재하지 않는 플랫폼입니다. platformId=" + platformId);
        }
        boolean alreadyMapped = jobPlatformMappingMapper.findByJobId(jobId).stream()
                .anyMatch(mapping -> mapping.getPlatformId().equals(platformId));
        if (alreadyMapped) {
            throw new IllegalArgumentException(
                    "이미 등록된 잡-플랫폼 매핑입니다. jobId=" + jobId + ", platformId=" + platformId);
        }

        JobPlatformMapping mapping = JobPlatformMapping.builder()
                .jobId(jobId)
                .platformId(platformId)
                .build();
        jobPlatformMappingMapper.insert(mapping);
        return mapping;
    }

    public void deleteById(Long mappingId) {
        jobPlatformMappingMapper.deleteById(mappingId);
    }
}
