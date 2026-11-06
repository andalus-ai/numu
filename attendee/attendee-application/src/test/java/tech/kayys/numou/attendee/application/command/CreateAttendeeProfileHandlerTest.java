package tech.kayys.numou.attendee.application.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.attendee.application.port.AttendeeAuthorizationPort;
import tech.kayys.numou.attendee.application.port.AttendeeProfileRepository;
import tech.kayys.numou.attendee.application.port.AttendeeTenantContext;
import tech.kayys.numou.attendee.application.port.AttendeeTenantContextProvider;
import tech.kayys.numou.attendee.application.port.AttendeePartyAccessPort;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CreateAttendeeProfileHandlerTest {

    private final TenantId trustedTenant = TenantId.generate();
    private final UUID trustedActor = UUID.randomUUID();

    @Test
    void derivesTenantAndActorFromTrustedContextAndRejectsUnknownParty() {
        var authorizationCalled = new AtomicBoolean();
        var repositoryCalled = new AtomicBoolean();
        var partyAccessCalled = new AtomicBoolean();
        var partyAccess = (AttendeePartyAccessPort) (tenantId, actorId, partyId) -> {
            partyAccessCalled.set(true);
            assertEquals(trustedTenant, tenantId);
            assertEquals(trustedActor, actorId);
            return Uni.createFrom().voidItem();
        };
        var repository = new AttendeeProfileRepository() {
            @Override
            public Uni<tech.kayys.numou.attendee.domain.AttendeeProfile> save(
                    tech.kayys.numou.attendee.domain.AttendeeProfile profile) {
                repositoryCalled.set(true);
                return Uni.createFrom().item(profile);
            }

            @Override
            public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantAndId(
                    TenantId tenantId, UUID profileId) {
                return Uni.createFrom().item(Optional.empty());
            }

            @Override
            public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantPartyAndKind(
                    TenantId tenantId, UUID partyId, String profileKind) {
                assertEquals(trustedTenant, tenantId);
                return Uni.createFrom().item(Optional.empty());
            }
        };
        var context = (AttendeeTenantContextProvider) () ->
                new AttendeeTenantContext(trustedTenant, trustedActor);
        var authorization = (AttendeeAuthorizationPort) request -> {
            authorizationCalled.set(true);
            assertEquals(trustedTenant, request.tenantId());
            assertEquals(trustedActor, request.actorId());
            assertEquals(AttendeeAuthorizationPort.Action.CREATE_PROFILE, request.action());
            return Uni.createFrom().voidItem();
        };

        var handler = new CreateAttendeeProfileHandler(repository, partyAccess, context,
                authorization, directUnitOfWork(), Clock.fixed(
                Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));

        var result = handler.handle(new CreateAttendeeProfileCommand(
                UUID.randomUUID(), "EVENT_GUEST", "Guest", null, "id-ID")).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("ATTENDEE_PARTY_NOT_FOUND", result.failureError().orElseThrow().code());
        assertTrue(authorizationCalled.get());
        assertTrue(partyAccessCalled.get());
        assertFalse(repositoryCalled.get());
    }

    @Test
    void authorizationFailureStopsBeforePartyLookup() {
        var partyAccessCalled = new AtomicBoolean();
        var partyAccess = (AttendeePartyAccessPort) (tenantId, actorId, partyId) -> {
            partyAccessCalled.set(true);
            return Uni.createFrom().voidItem();
        };
        var repository = new NoopRepository();
        var authorization = (AttendeeAuthorizationPort) request ->
                Uni.createFrom().failure(new SecurityException("forbidden"));

        var handler = new CreateAttendeeProfileHandler(repository, partyAccess,
                () -> new AttendeeTenantContext(trustedTenant, trustedActor),
                authorization, directUnitOfWork(), Clock.systemUTC());

        assertThrows(SecurityException.class, () -> handler.handle(
                new CreateAttendeeProfileCommand(UUID.randomUUID(), "EVENT_GUEST",
                        "Guest", null, null)).await().indefinitely());
        assertFalse(partyAccessCalled.get());
    }

    private static UnitOfWork directUnitOfWork() {
        return new UnitOfWork() {
            @Override
            public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> work) {
                return work.get();
            }
        };
    }

    private static final class NoopRepository implements AttendeeProfileRepository {
        @Override
        public Uni<tech.kayys.numou.attendee.domain.AttendeeProfile> save(
                tech.kayys.numou.attendee.domain.AttendeeProfile profile) {
            return Uni.createFrom().item(profile);
        }

        @Override
        public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantAndId(
                TenantId tenantId, UUID profileId) {
            return Uni.createFrom().item(Optional.empty());
        }

        @Override
        public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantPartyAndKind(
                TenantId tenantId, UUID partyId, String profileKind) {
            return Uni.createFrom().item(Optional.empty());
        }
    }
}
