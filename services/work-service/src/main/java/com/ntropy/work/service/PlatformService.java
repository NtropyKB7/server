package com.ntropy.work.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.mapper.PlatformMapper;

import lombok.RequiredArgsConstructor;

/**
 * PLATFORM은 마스터 데이터라 조회만 제공한다. (등록/수정은 시딩으로만 관리)
 */
@Service
@RequiredArgsConstructor
public class PlatformService {

    private final PlatformMapper platformMapper;

    public List<Platform> findAll() {
        return platformMapper.findAll();
    }

    public Platform findById(Long platformId) {
        Platform platform = platformMapper.findById(platformId);
        if (platform == null) {
            throw new IllegalArgumentException("존재하지 않는 플랫폼입니다. platformId=" + platformId);
        }
        return platform;
    }
}
