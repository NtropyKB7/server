package com.ntropy.work.mapper;

import java.util.List;

import com.ntropy.work.domain.entity.Platform;

public interface PlatformMapper {

    void insert(Platform platform);

    Platform findById(Long platformId);

    List<Platform> findByJobId(Long jobId);

    void update(Platform platform);

    void deleteById(Long platformId);
}
