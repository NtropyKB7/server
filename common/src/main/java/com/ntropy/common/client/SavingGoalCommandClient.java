package com.ntropy.common.client;

import com.ntropy.common.dto.work.command.SavingGoalRegisterCommand;

/**
 * work-service의 SAVING_GOAL 쓰기 계약. work-service가 LocalSavingGoalCommandClient로 구현하고,
 * bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface SavingGoalCommandClient {

    Long registerSavingGoal(SavingGoalRegisterCommand command);
}
