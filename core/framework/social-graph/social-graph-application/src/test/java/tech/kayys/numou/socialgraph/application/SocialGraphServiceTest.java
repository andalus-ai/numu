package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.spi.SocialGraphIdempotencyRecord;
import tech.kayys.numou.socialgraph.spi.SocialGraphOutboxEvent;
import tech.kayys.numou.socialgraph.spi.SocialGraphTransaction;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialGraphServiceTest {
    private final TenantId tenant = TenantId.generate();
    private final PartyId requester = PartyId.generate();
    private final PartyId recipient = PartyId.generate();
    private final MemoryUnitOfWork uow = new MemoryUnitOfWork();
    private final SocialGraphService service = new SocialGraphService(
            uow, party -> Uni.createFrom().item(true), (actor, action) -> {}, Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void requestIsPersistedWithOutboxAndIdempotencyInOneUnitOfWork() {
        SocialConnection first = service.requestConnection(new SocialGraphActor(tenant, requester), recipient, "request-1").await().indefinitely();
        SocialConnection replay = service.requestConnection(new SocialGraphActor(tenant, requester), recipient, "request-1").await().indefinitely();

        assertThat(first.id()).isEqualTo(replay.id());
        assertThat(uow.connections).hasSize(1);
        assertThat(uow.events).hasSize(1);
        assertThat(uow.audits).hasSize(1);
        assertThat(uow.idempotency).hasSize(1);
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentCommand() {
        service.requestConnection(new SocialGraphActor(tenant, requester), recipient, "request-2").await().indefinitely();
        PartyId other = PartyId.generate();

        assertThatThrownBy(() -> service.requestConnection(new SocialGraphActor(tenant, requester), other, "request-2").await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void idempotencyFingerprintIncludesAuthenticatedActor() {
        service.requestConnection(new SocialGraphActor(tenant, requester), recipient, "shared-key").await().indefinitely();
        PartyId other = PartyId.generate();

        assertThatThrownBy(() -> service.requestConnection(new SocialGraphActor(tenant, recipient), other, "shared-key").await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateOpenConnectionIsRejectedEvenWithDifferentKey() {
        service.requestConnection(new SocialGraphActor(tenant, requester), recipient, "request-3").await().indefinitely();

        assertThatThrownBy(() -> service.requestConnection(new SocialGraphActor(tenant, requester), recipient, "request-4").await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recipientCanAcceptAndRequesterCanCancelButWrongActorCannotAccept() {
        SocialConnection pending = service.requestConnection(new SocialGraphActor(tenant, requester), recipient, "request-5").await().indefinitely();

        assertThatThrownBy(() -> service.acceptConnection(new SocialGraphActor(tenant, requester), pending.id(), "accept-wrong").await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);

        SocialConnection active = service.acceptConnection(new SocialGraphActor(tenant, recipient), pending.id(), "accept-1").await().indefinitely();
        assertThat(active.status().name()).isEqualTo("ACTIVE");
    }


    @Test
    void requestAndLifecycleCommandsRequireDifferentPermissions() {
        var requested = new java.util.ArrayList<SocialGraphAuthorizationAction>();
        SocialGraphService secured = new SocialGraphService(
                uow, party -> Uni.createFrom().item(true),
                (actor, action) -> requested.add(action),
                Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));

        SocialConnection pending = secured.requestConnection(
                new SocialGraphActor(tenant, requester), recipient, "permission-request").await().indefinitely();
        secured.acceptConnection(
                new SocialGraphActor(tenant, recipient), pending.id(), "permission-accept").await().indefinitely();

        assertThat(requested).containsExactly(
                SocialGraphAuthorizationAction.REQUEST_CONNECTION,
                SocialGraphAuthorizationAction.MANAGE_CONNECTION);
    }

    @Test
    void deniedAuthorizationFailsReactiveCommandWithoutSideEffects() {
        SocialGraphService denied = new SocialGraphService(
                uow, party -> Uni.createFrom().item(true),
                (actor, action) -> { throw new SocialGraphAccessDeniedException(); },
                Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> denied.requestConnection(
                new SocialGraphActor(tenant, requester), recipient, "denied-request").await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);

        assertThat(uow.connections).isEmpty();
        assertThat(uow.events).isEmpty();
        assertThat(uow.audits).isEmpty();
        assertThat(uow.idempotency).isEmpty();
    }

    private static final class MemoryUnitOfWork implements SocialGraphUnitOfWork {
        private final Map<SocialConnectionId, SocialConnection> connections = new HashMap<>();
        private final Map<String, SocialGraphIdempotencyRecord> idempotency = new HashMap<>();
        private final Map<UUID, SocialGraphOutboxEvent> events = new HashMap<>();
        private final java.util.List<tech.kayys.numou.socialgraph.spi.SocialGraphAuditRecord> audits = new java.util.ArrayList<>();

        @Override
        public <T> Uni<T> execute(java.util.function.Function<SocialGraphTransaction, T> work) {
            return Uni.createFrom().item(() -> work.apply(new SocialGraphTransaction() {
                @Override public Optional<SocialConnection> findConnection(TenantId tenant, SocialConnectionId id) {
                    return Optional.ofNullable(connections.get(id)).filter(c -> c.tenantId().equals(tenant));
                }
                @Override public Optional<SocialConnection> findOpenConnection(TenantId tenant, PartyId a, PartyId b) {
                    return connections.values().stream().filter(c -> c.tenantId().equals(tenant))
                            .filter(c -> c.status().name().equals("PENDING") || c.status().name().equals("ACTIVE"))
                            .filter(c -> (c.requesterPartyId().equals(a) && c.recipientPartyId().equals(b))
                                    || (c.requesterPartyId().equals(b) && c.recipientPartyId().equals(a))).findFirst();
                }
                @Override public void insertConnection(SocialConnection c) { connections.put(c.id(), c); }
                @Override public void updateConnection(SocialConnection c, int expectedVersion) {
                    SocialConnection current = connections.get(c.id());
                    if (current == null || current.version() != expectedVersion) throw new IllegalStateException("version conflict");
                    connections.put(c.id(), c);
                }
                @Override public Optional<SocialGraphIdempotencyRecord> findIdempotency(TenantId tenant, String key) {
                    return Optional.ofNullable(idempotency.get(tenant + ":" + key));
                }
                @Override public void saveIdempotency(SocialGraphIdempotencyRecord record) {
                    idempotency.put(record.tenantId() + ":" + record.key(), record);
                }
                @Override public void appendOutbox(SocialGraphOutboxEvent event) { events.put(event.eventId(), event); }
                @Override public void appendAudit(tech.kayys.numou.socialgraph.spi.SocialGraphAuditRecord record) { audits.add(record); }
            }));
        }
    }
}
