package com.ntropy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ntropy.account.config.FinancialSyncBatchUserScopeProperties;
import com.ntropy.ai.config.AiReportBatchUserScopeProperties;
import com.ntropy.common.domain.UserScope;
import com.ntropy.work.config.SettlementBatchUserScopeProperties;
import com.ntropy.work.config.WorkReminderBatchUserScopeProperties;

class BatchUserScopeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void usesRealOnlyDefaultsWhenExternalFileDoesNotExist() {
        withConfigDirectory(tempDir.resolve("missing"), context -> {
            assertEquals(
                    UserScope.REAL_ONLY,
                    context.getBean(FinancialSyncBatchUserScopeProperties.class).getUserScope()
            );
            assertEquals(
                    UserScope.REAL_ONLY,
                    context.getBean(SettlementBatchUserScopeProperties.class).getUserScope()
            );
            assertEquals(
                    UserScope.REAL_ONLY,
                    context.getBean(WorkReminderBatchUserScopeProperties.class).getUserScope()
            );
            assertEquals(
                    UserScope.REAL_ONLY,
                    context.getBean(AiReportBatchUserScopeProperties.class).getUserScope()
            );
        });
    }

    @Test
    void externalFileOverridesOnlyTheConfiguredBatch() throws IOException {
        Files.writeString(
                tempDir.resolve("batch.properties"),
                "batch.settlement.user-scope=VIRTUAL_ONLY\n"
        );

        withConfigDirectory(tempDir, context -> {
            assertEquals(
                    UserScope.REAL_ONLY,
                    context.getBean(FinancialSyncBatchUserScopeProperties.class).getUserScope()
            );
            assertEquals(
                    UserScope.VIRTUAL_ONLY,
                    context.getBean(SettlementBatchUserScopeProperties.class).getUserScope()
            );
            assertEquals(
                    UserScope.REAL_ONLY,
                    context.getBean(WorkReminderBatchUserScopeProperties.class).getUserScope()
            );
            assertEquals(
                    UserScope.REAL_ONLY,
                    context.getBean(AiReportBatchUserScopeProperties.class).getUserScope()
            );
        });
    }

    @Test
    void unknownScopeValue_failsApplicationContextStartupInsteadOfFallingBackToAll() throws IOException {
        Files.writeString(
                tempDir.resolve("batch.properties"),
                "batch.settlement.user-scope=NONSENSE\n"
        );

        String previous = System.getProperty("NTROPY_CONFIG_DIR");
        System.setProperty("NTROPY_CONFIG_DIR", tempDir.toAbsolutePath().toString());
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(
                    BatchUserScopeConfig.class,
                    FinancialSyncBatchUserScopeProperties.class,
                    SettlementBatchUserScopeProperties.class,
                    WorkReminderBatchUserScopeProperties.class,
                    AiReportBatchUserScopeProperties.class
            );

            BeanCreationException exception = assertThrows(BeanCreationException.class, context::refresh);

            // UserScope.fromConfigValue는 IllegalArgumentException(Enum.valueOf 실패)을 IllegalStateException으로
            // 감싸 다시 던진다 - 원인 체인 맨 끝(가장 깊은 cause)이 아니라, 우리가 던진 그 IllegalStateException
            // 자체를 찾아야 "ALL로 완화되지 않고 fail-closed됐다"는 의도를 검증할 수 있다.
            Throwable configFailure = findCause(exception, IllegalStateException.class);
            assertTrue(
                    configFailure != null
                            && configFailure.getMessage() != null
                            && configFailure.getMessage().contains("NONSENSE"),
                    "알 수 없는 user-scope 값(NONSENSE)이 ALL로 완화되지 않고 컨텍스트 기동을 막아야 합니다. "
                            + "실제 예외 체인: " + exception
            );
        } finally {
            if (previous == null) {
                System.clearProperty("NTROPY_CONFIG_DIR");
            } else {
                System.setProperty("NTROPY_CONFIG_DIR", previous);
            }
        }
    }

    private static Throwable findCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static void withConfigDirectory(
            Path configDirectory,
            java.util.function.Consumer<AnnotationConfigApplicationContext> assertion
    ) {
        String previous = System.getProperty("NTROPY_CONFIG_DIR");
        System.setProperty("NTROPY_CONFIG_DIR", configDirectory.toAbsolutePath().toString());
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(
                    BatchUserScopeConfig.class,
                    FinancialSyncBatchUserScopeProperties.class,
                    SettlementBatchUserScopeProperties.class,
                    WorkReminderBatchUserScopeProperties.class,
                    AiReportBatchUserScopeProperties.class
            );
            context.refresh();
            assertion.accept(context);
        } finally {
            if (previous == null) {
                System.clearProperty("NTROPY_CONFIG_DIR");
            } else {
                System.setProperty("NTROPY_CONFIG_DIR", previous);
            }
        }
    }
}
