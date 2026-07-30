package com.ntropy.common.client;

import com.ntropy.common.dto.work.command.WorkLogPatchCommand;
import com.ntropy.common.dto.work.command.WorkLogRegisterCommand;

/**
 * work-service의 WORK_LOG 쓰기 계약. work-service가 LocalWorkLogCommandClient로 구현하고,
 * bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface WorkLogCommandClient {

    Long registerPlan(WorkLogRegisterCommand command);

    Long registerActual(WorkLogRegisterCommand command);

    void editWorkLog(Long logId, WorkLogPatchCommand command);

    void confirmWorkLog(Long logId, WorkLogPatchCommand command);

    void deleteWorkLog(Long logId);
}
