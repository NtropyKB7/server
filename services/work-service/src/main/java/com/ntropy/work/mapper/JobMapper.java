package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ntropy.work.domain.entity.Job;

@Mapper
public interface JobMapper {

    void insert(Job job);

    Job findById(Long jobId);

    List<Job> findByUserId(Long userId);

    void update(Job job);

    void deleteById(Long jobId);
}
