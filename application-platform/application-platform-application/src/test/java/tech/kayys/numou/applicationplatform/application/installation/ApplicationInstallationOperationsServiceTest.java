package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOperationsPort.*;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationInstallationOperationsServiceTest {
    @Test
    void deniedRedriveNeverReachesPersistencePort() {
        TenantId tenant = TenantId.generate();
        AtomicInteger redriveCalls = new AtomicInteger();
        ApplicationInstallationOperationsPort operations = new ApplicationInstallationOperationsPort() {
            @Override public CompletableFuture<List<TransitionEntry>> history(
                    TenantId ignored, ApplicationInstallationId id, int limit) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<List<DeadLetterEntry>> deadLetters(TenantId ignored, int limit) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<List<RedriveAuditEntry>> redriveHistory(
                    TenantId ignored, ApplicationInstallationId id, int limit) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<RedriveResult> redrive(RedriveCommand command) {
                redriveCalls.incrementAndGet();
                return CompletableFuture.completedFuture(new RedriveResult(RedriveResult.Outcome.QUEUED, null, null));
            }
        };
        ApplicationInstallationOperatorAuthorizationPort authorization =
                new ApplicationInstallationOperatorAuthorizationPort() {
                    @Override public CompletableFuture<Void> assertCanInspect(TenantId ignored, String actor) {
                        return CompletableFuture.completedFuture(null);
                    }
                    @Override public CompletableFuture<Void> assertCanRedrive(TenantId ignored, String actor) {
                        return CompletableFuture.failedFuture(new SecurityException("missing installation:redrive permission"));
                    }
                };
        var service = new ApplicationInstallationOperationsService(operations, authorization);
        var command = new RedriveCommand(tenant, ApplicationInstallationId.generate(), "operator:alice",
                "investigated", "a".repeat(64), Instant.parse("2026-11-14T00:00:00Z"));

        assertThatThrownBy(() -> service.redrive(command).toCompletableFuture().join())
                .hasCauseInstanceOf(SecurityException.class);
        assertThat(redriveCalls).hasValue(0);
    }

    @Test
    void inspectionRequiresHostAuthorizationBeforeReadingTenantData() {
        TenantId tenant = TenantId.generate();
        AtomicInteger reads = new AtomicInteger();
        ApplicationInstallationOperationsPort operations = new ApplicationInstallationOperationsPort() {
            @Override public CompletableFuture<List<TransitionEntry>> history(
                    TenantId ignored, ApplicationInstallationId id, int limit) {
                reads.incrementAndGet();
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<List<DeadLetterEntry>> deadLetters(TenantId ignored, int limit) {
                reads.incrementAndGet();
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<List<RedriveAuditEntry>> redriveHistory(
                    TenantId ignored, ApplicationInstallationId id, int limit) {
                reads.incrementAndGet();
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<RedriveResult> redrive(RedriveCommand command) {
                return CompletableFuture.failedFuture(new AssertionError("not expected"));
            }
        };
        ApplicationInstallationOperatorAuthorizationPort authorization =
                new ApplicationInstallationOperatorAuthorizationPort() {
                    @Override public CompletableFuture<Void> assertCanInspect(TenantId ignored, String actor) {
                        return CompletableFuture.failedFuture(new SecurityException("tenant access denied"));
                    }
                    @Override public CompletableFuture<Void> assertCanRedrive(TenantId ignored, String actor) {
                        return CompletableFuture.completedFuture(null);
                    }
                };
        var service = new ApplicationInstallationOperationsService(operations, authorization);

        assertThatThrownBy(() -> service.deadLetters(tenant, "operator:bob", 10).toCompletableFuture().join())
                .hasCauseInstanceOf(SecurityException.class);
        assertThat(reads).hasValue(0);
    }
}
