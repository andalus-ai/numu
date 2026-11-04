package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultancyEvidenceOperationsAdminServiceTest {
    private static final String TENANT = "tenant-a";
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID OPERATION = UUID.randomUUID();

    @Test
    void authorizesInspectionBeforeCallingStore() {
        AtomicBoolean called = new AtomicBoolean();
        var store = new FakeStore(called);
        var auth = (ConsultancyEvidenceOperationsAdminAuthorization) (tenant, actor, action) -> Uni.createFrom().voidItem();
        var service = new ConsultancyEvidenceOperationsAdminService(store, auth,
                Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC));
        var result = service.list(TENANT, ACTOR, Optional.empty(), 20, 0).await().indefinitely();
        assertThat(result).isEmpty();
        assertThat(called).isTrue();
    }

    @Test
    void deniedInspectionNeverReachesStore() {
        AtomicBoolean called = new AtomicBoolean();
        var store = new FakeStore(called);
        var auth = (ConsultancyEvidenceOperationsAdminAuthorization) (tenant, actor, action) ->
                Uni.createFrom().failure(new SecurityException("denied"));
        var service = new ConsultancyEvidenceOperationsAdminService(store, auth, Clock.systemUTC());
        assertThatThrownBy(() -> service.list(TENANT, ACTOR, Optional.empty(), 20, 0)
                .await().indefinitely()).hasCauseInstanceOf(SecurityException.class);
        assertThat(called).isFalse();
    }

    @Test
    void snapshotRequiresViewAuthorization() {
        AtomicBoolean called = new AtomicBoolean();
        var service = new ConsultancyEvidenceOperationsAdminService(new FakeStore(called),
                (tenant, actor, action) -> {
                    assertThat(action).isEqualTo(ConsultancyEvidenceOperationsAdminAuthorization.Action.VIEW);
                    return Uni.createFrom().voidItem();
                }, Clock.systemUTC());
        var snapshot = service.snapshot(TENANT, ACTOR).await().indefinitely();
        assertThat(snapshot.pendingCount()).isEqualTo(2);
        assertThat(called).isTrue();
    }

    @Test
    void recoveryHistoryUsesDedicatedAuthorization() {
        AtomicBoolean called = new AtomicBoolean();
        var service = new ConsultancyEvidenceOperationsAdminService(new FakeStore(called),
                (tenant, actor, action) -> {
                    assertThat(action).isEqualTo(
                            ConsultancyEvidenceOperationsAdminAuthorization.Action.VIEW_RECOVERY_HISTORY);
                    return Uni.createFrom().voidItem();
                }, Clock.systemUTC());
        var history = service.recoveryHistory(TENANT, OPERATION, ACTOR, 10, 0).await().indefinitely();
        assertThat(history).hasSize(1);
        assertThat(called).isTrue();
    }

    @Test
    void recoveryCarriesActorReasonAndIdempotencyKeyAfterAuthorization() {
        AtomicBoolean called = new AtomicBoolean();
        var store = new FakeStore(called);
        var auth = (ConsultancyEvidenceOperationsAdminAuthorization) (tenant, actor, action) -> {
            assertThat(tenant).isEqualTo(TENANT);
            assertThat(actor).isEqualTo(ACTOR);
            assertThat(action).isEqualTo(ConsultancyEvidenceOperationsAdminAuthorization.Action.RECOVER_TERMINAL_FAILURE);
            return Uni.createFrom().voidItem();
        };
        var service = new ConsultancyEvidenceOperationsAdminService(store, auth,
                Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC));
        var recovered = service.recoverTerminalFailure(
                TENANT, OPERATION, ACTOR, "Corrected source permissions", "recover-001").await().indefinitely();
        assertThat(called).isTrue();
        assertThat(recovered.operationId()).isEqualTo(OPERATION);
    }

    @Test
    void rejectsUnboundedInspectionPage() {
        var service = new ConsultancyEvidenceOperationsAdminService(new FakeStore(new AtomicBoolean()),
                (tenant, actor, action) -> Uni.createFrom().voidItem(), Clock.systemUTC());
        assertThatThrownBy(() -> service.list(TENANT, ACTOR, Optional.empty(), 201, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeStore implements ConsultancyEvidenceOperationsAdminStore {
        private final AtomicBoolean called;
        private FakeStore(AtomicBoolean called) { this.called = called; }

        @Override
        public Uni<List<ConsultancyEvidenceOperation>> list(String tenantId,
                Optional<ConsultancyEvidenceOperation.Status> status, int limit, int offset) {
            called.set(true);
            return Uni.createFrom().item(List.of());
        }

        @Override
        public Uni<ConsultancyEvidenceOperationsSnapshot> snapshot(String tenantId) {
            called.set(true);
            return Uni.createFrom().item(new ConsultancyEvidenceOperationsSnapshot(2, 1, 1, 4, null, null, null));
        }

        @Override
        public Uni<List<ConsultancyEvidenceRecoveryAuditEntry>> recoveryHistory(
                String tenantId, UUID operationId, int limit, int offset) {
            called.set(true);
            return Uni.createFrom().item(List.of(new ConsultancyEvidenceRecoveryAuditEntry(
                    UUID.randomUUID(), operationId, ACTOR, "manual correction", "recover-001",
                    ConsultancyEvidenceOperation.Status.TERMINAL_FAILURE,
                    ConsultancyEvidenceOperation.Status.PENDING, Instant.parse("2026-11-02T00:00:00Z"))));
        }

        @Override
        public Uni<ConsultancyEvidenceOperation> recoverTerminalFailure(RecoveryRequest request) {
            called.set(true);
            assertThat(request.actorId()).isEqualTo(ACTOR);
            assertThat(request.reason()).isEqualTo("Corrected source permissions");
            assertThat(request.idempotencyKey()).isEqualTo("recover-001");
            return Uni.createFrom().item(ConsultancyEvidenceOperation.pending(
                    request.operationId(), request.tenantId(), "original-request", UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), "supporting-evidence", request.actorId(),
                    UUID.randomUUID(), request.occurredAt()));
        }
    }
}
