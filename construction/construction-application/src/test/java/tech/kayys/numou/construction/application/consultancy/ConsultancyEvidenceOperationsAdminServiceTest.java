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
        var auth = (ConsultancyEvidenceOperationsAdminAuthorization) (tenant, actor, action) ->
                Uni.createFrom().voidItem();
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
        var service = new ConsultancyEvidenceOperationsAdminService(store, auth,
                Clock.systemUTC());

        assertThatThrownBy(() -> service.list(TENANT, ACTOR, Optional.empty(), 20, 0)
                .await().indefinitely()).hasCauseInstanceOf(SecurityException.class);
        assertThat(called).isFalse();
    }

    @Test
    void recoveryCarriesActorReasonAndIdempotencyKeyAfterAuthorization() {
        AtomicBoolean called = new AtomicBoolean();
        var store = new FakeStore(called);
        var auth = (ConsultancyEvidenceOperationsAdminAuthorization) (tenant, actor, action) -> {
            assertThat(tenant).isEqualTo(TENANT);
            assertThat(actor).isEqualTo(ACTOR);
            assertThat(action).isEqualTo(
                    ConsultancyEvidenceOperationsAdminAuthorization.Action.RECOVER_TERMINAL_FAILURE);
            return Uni.createFrom().voidItem();
        };
        var service = new ConsultancyEvidenceOperationsAdminService(store, auth,
                Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC));

        var recovered = service.recoverTerminalFailure(
                TENANT, OPERATION, ACTOR, "Corrected source permissions", "recover-001")
                .await().indefinitely();

        assertThat(called).isTrue();
        assertThat(recovered.operationId()).isEqualTo(OPERATION);
    }

    @Test
    void rejectsUnboundedInspectionPage() {
        var service = new ConsultancyEvidenceOperationsAdminService(
                new FakeStore(new AtomicBoolean()),
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
