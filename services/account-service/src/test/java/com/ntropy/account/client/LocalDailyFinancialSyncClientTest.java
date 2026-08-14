package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.service.BatchExecutionLeaseService;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.account.service.DailyCodefSyncService;
import com.ntropy.account.service.DailyNtropySyncService;
import com.ntropy.common.domain.DailyFinancialSyncProvider;
import com.ntropy.common.dto.account.DailyFinancialSyncResult;
import com.ntropy.common.dto.account.DailyFinancialSyncResult.InstitutionSyncResult;

class LocalDailyFinancialSyncClientTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 14);

    @Test
    void delegatesToCodefSyncServiceAndCompletesLeaseOnSuccess() {
        StubLeaseService leaseService = new StubLeaseService(true);
        StubCodefSyncService codefSync = new StubCodefSyncService(successResult(DailyFinancialSyncProvider.CODEF));
        StubNtropySyncService ntropySync = new StubNtropySyncService(null);
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(leaseService, codefSync, ntropySync);

        DailyFinancialSyncResult result = client.synchronize(DailyFinancialSyncProvider.CODEF, List.of(1L), BUSINESS_DATE);

        assertEquals("SUCCESS", result.executionStatus());
        assertTrue(codefSync.called);
        assertFalse(ntropySync.called);
        assertEquals(DailyCodefSyncService.JOB_NAME, leaseService.lastAcquireJobName);
        assertEquals(BatchExecutionStatus.SUCCESS, leaseService.lastCompleteStatus);
        assertNull(leaseService.lastCompleteErrorSummary);
    }

    @Test
    void delegatesToNtropySyncServiceForNtropyProvider() {
        StubLeaseService leaseService = new StubLeaseService(true);
        StubCodefSyncService codefSync = new StubCodefSyncService(null);
        StubNtropySyncService ntropySync = new StubNtropySyncService(successResult(DailyFinancialSyncProvider.NTROPY));
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(leaseService, codefSync, ntropySync);

        DailyFinancialSyncResult result = client.synchronize(DailyFinancialSyncProvider.NTROPY, List.of(1L), BUSINESS_DATE);

        assertEquals("SUCCESS", result.executionStatus());
        assertTrue(ntropySync.called);
        assertFalse(codefSync.called);
        assertEquals(DailyNtropySyncService.JOB_NAME, leaseService.lastAcquireJobName);
    }

    @Test
    void returnsSkippedResultWithoutSynchronizingWhenLeaseIsAlreadyHeld() {
        StubLeaseService leaseService = new StubLeaseService(false);
        StubCodefSyncService codefSync = new StubCodefSyncService(null);
        StubNtropySyncService ntropySync = new StubNtropySyncService(null);
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(leaseService, codefSync, ntropySync);

        DailyFinancialSyncResult result = client.synchronize(DailyFinancialSyncProvider.CODEF, List.of(1L), BUSINESS_DATE);

        assertEquals("SKIPPED", result.executionStatus());
        assertFalse(codefSync.called);
        assertTrue(result.successfulUserIds().isEmpty());
        assertFalse(leaseService.completeCalled);
    }

    @Test
    void buildsErrorSummaryWithoutSensitiveFieldsWhenPartialFailed() {
        StubLeaseService leaseService = new StubLeaseService(true);
        DailyFinancialSyncResult partialFailed = new DailyFinancialSyncResult(
                BUSINESS_DATE, DailyFinancialSyncProvider.CODEF, "PARTIAL_FAILED",
                List.of(), Map.of(), List.of(2L),
                List.of(new InstitutionSyncResult("0004", "PARTIAL_FAILED", "TIMEOUT")),
                3
        );
        StubCodefSyncService codefSync = new StubCodefSyncService(partialFailed);
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(
                leaseService, codefSync, new StubNtropySyncService(null)
        );

        client.synchronize(DailyFinancialSyncProvider.CODEF, List.of(2L), BUSINESS_DATE);

        assertEquals(BatchExecutionStatus.PARTIAL_FAILED, leaseService.lastCompleteStatus);
        assertTrue(leaseService.lastCompleteErrorSummary.contains("0004"));
        assertTrue(leaseService.lastCompleteErrorSummary.contains("TIMEOUT"));
        assertFalse(leaseService.lastCompleteErrorSummary.contains("connectedId"));
    }

    private static DailyFinancialSyncResult successResult(DailyFinancialSyncProvider provider) {
        return new DailyFinancialSyncResult(
                BUSINESS_DATE, provider, "SUCCESS", List.of(1L), Map.of(), List.of(), List.of(), 5
        );
    }

    private static class StubLeaseService extends BatchExecutionLeaseService {

        private final boolean acquireSucceeds;
        private String lastAcquireJobName;
        private boolean completeCalled;
        private BatchExecutionStatus lastCompleteStatus;
        private String lastCompleteErrorSummary;

        StubLeaseService(boolean acquireSucceeds) {
            super(null, null, null);
            this.acquireSucceeds = acquireSucceeds;
        }

        @Override
        public Optional<LeaseHandle> acquire(String jobName, LocalDate businessDate, String ownerId) {
            lastAcquireJobName = jobName;
            if (!acquireSucceeds) {
                return Optional.empty();
            }
            return Optional.of(new LeaseHandle(1L, jobName, businessDate, ownerId, "token"));
        }

        @Override
        public boolean complete(LeaseHandle lease, BatchExecutionStatus status, String errorSummaryJson) {
            completeCalled = true;
            lastCompleteStatus = status;
            lastCompleteErrorSummary = errorSummaryJson;
            return true;
        }
    }

    private static class StubCodefSyncService extends DailyCodefSyncService {

        private final DailyFinancialSyncResult result;
        private boolean called;

        StubCodefSyncService(DailyFinancialSyncResult result) {
            super(null, null, null, null, null, null, null, null);
            this.result = result;
        }

        @Override
        public DailyFinancialSyncResult synchronize(List<Long> activeUserIds, LocalDate businessDate, LeaseHandle lease) {
            called = true;
            return result;
        }
    }

    private static class StubNtropySyncService extends DailyNtropySyncService {

        private final DailyFinancialSyncResult result;
        private boolean called;

        StubNtropySyncService(DailyFinancialSyncResult result) {
            super(null, null, null, null, null, null, null, null);
            this.result = result;
        }

        @Override
        public DailyFinancialSyncResult synchronize(List<Long> activeUserIds, LocalDate businessDate, LeaseHandle lease) {
            called = true;
            return result;
        }
    }
}
