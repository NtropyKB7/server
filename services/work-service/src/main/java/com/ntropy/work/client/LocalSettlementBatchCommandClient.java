package com.ntropy.work.client;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.SettlementBatchCommandClient;
import com.ntropy.work.service.SettlementService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalSettlementBatchCommandClient implements SettlementBatchCommandClient {

    private final SettlementService settlementService;

    @Override
    public boolean runForDate(Long userId, LocalDate processDate) {
        boolean created = settlementService.processSettlement(userId, processDate);
        if (created) {
            settlementService.notifySettlementCompleted(userId, processDate);
        }
        return created;
    }
}
