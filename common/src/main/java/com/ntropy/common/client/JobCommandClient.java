package com.ntropy.common.client;

import com.ntropy.common.dto.work.command.JobRegisterCommand;
import com.ntropy.common.dto.work.command.JobUpdateCommand;

/**
 * work-service의 JOB 쓰기 계약. work-service가 LocalJobCommandClient로 구현하고,
 * bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface JobCommandClient {

    Long registerJob(JobRegisterCommand command);

    void updateJob(Long jobId, JobUpdateCommand command);

    void deactivateJob(Long jobId);
}
