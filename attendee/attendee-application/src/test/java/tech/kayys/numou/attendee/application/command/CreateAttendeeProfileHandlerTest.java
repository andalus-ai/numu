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
    void derivesTenantAndActorFromTrustedContextAndRequiresPartyAccessBeforePersistence() {
        var authorizationCalled = new AtomicBoolean();
        var repositoryCalled = new AtomicBoolean();
        var partyAccessCalled = new AtomicBoolean();
        var partyAccess = (AttendeePartyAccessPort) (tenantId, actorId, partyId) -> {
            partyAccessCalled.set(true);
            assertEquals(trustedTenant, tenantId);
            assertEquals(trustedActor, actorId);
            return Uni.createFrom().failure(new SecurityException("party is not accessible"));
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
        var trackingRepository = new AttendeeProfileRepository() {
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
                repositoryCalled.set(true);
                return Uni.createFrom().item(Optional.empty());
            }
        };

        var handler = new CreateAttendeeProfileHandler(trackingRepository, partyAccess, context,
                authorization, directUnitOfWork(), Clock.fixed(
                Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));

        assertThrows(SecurityException.class, () -> handler.handle(new CreateAttendeeProfileCommand(
                UUID.randomUUID(), "EVENT_GUEST", "Guest", null, "id-ID")).await().indefinitely());
        assertTrue(authorizationCalled.get());
        assertTrue(partyAccessCalled.get());
        assertFalse(repositoryCalled.get());
    }

    @Test
    void authorizationFailureStopsBeforePartyAccess() {
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


    @Test
    void createsPendingProfileUsingTrustedTenantAndCanonicalPartyReference() {
        var partyId = UUID.randomUUID();
        var saved = new java.util.concurrent.atomic.AtomicReference<tech.kayys.numou.attendee.domain.AttendeeProfile>();
        var repository = new AttendeeProfileRepository() {
            @Override
            public Uni<tech.kayys.numou.attendee.domain.AttendeeProfile> save(
                    tech.kayys.numou.attendee.domain.AttendeeProfile profile) {
                saved.set(profile);
                return Uni.createFrom().item(profile);
            }
            @Override
            public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantAndId(
                    TenantId tenantId, UUID profileId) {
                return Uni.createFrom().item(Optional.empty());
            }
            @Override
            public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantPartyAndKind(
                    TenantId tenantId, UUID requestedPartyId, String profileKind) {
                assertEquals(trustedTenant, tenantId);
                assertEquals(partyId, requestedPartyId);
                assertEquals("EVENT_GUEST", profileKind);
                return Uni.createFrom().item(Optional.empty());
            }
        };
        var partyAccess = (AttendeePartyAccessPort) (tenantId, actorId, requestedPartyId) -> {
            assertEquals(trustedTenant, tenantId);
            assertEquals(trustedActor, actorId);
            assertEquals(partyId, requestedPartyId.value());
            return Uni.createFrom().voidItem();
        };
        var handler = new CreateAttendeeProfileHandler(repository, partyAccess,
                () -> new AttendeeTenantContext(trustedTenant, trustedActor),
                request -> Uni.createFrom().voidItem(), directUnitOfWork(),
                Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));

        var result = handler.handle(new CreateAttendeeProfileCommand(
                partyId, "EVENT_GUEST", "Guest", "Preferred", "id-ID")).await().indefinitely();

        assertTrue(result.isSuccess());
        assertNotNull(saved.get());
        assertEquals(trustedTenant, saved.get().tenantId());
        assertEquals(partyId, saved.get().partyId().value());
        assertEquals("Guest", saved.get().displayName());
        assertEquals("Preferred", saved.get().preferredName());
        assertEquals("id-ID", saved.get().preferredLanguage());
        assertEquals(0, result.orElseThrow().revision());
    }


    @Test
    void mapsConcurrentUniqueConstraintConflictToStableApplicationError() {
        var tenantId = trustedTenant;
        var partyId = UUID.randomUUID();
        var repository = new AttendeeProfileRepository() {
            @Override
            public Uni<tech.kayys.numou.attendee.domain.AttendeeProfile> save(
                    tech.kayys.numou.attendee.domain.AttendeeProfile profile) {
                return Uni.createFrom().failure(
                        new AttendeeProfileAlreadyExistsException("duplicate", null));
            }
            @Override
            public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantAndId(
                    TenantId tenantId, UUID profileId) {
                return Uni.createFrom().item(Optional.empty());
            }
            @Override
            public Uni<Optional<tech.kayys.numou.attendee.domain.AttendeeProfile>> findByTenantPartyAndKind(
                    TenantId requestedTenant, UUID requestedParty, String profileKind) {
                return Uni.createFrom().item(Optional.empty());
            }
        };
        var handler = new CreateAttendeeProfileHandler(repository,
                (tenant, actor, party) -> Uni.createFrom().voidItem(),
                () -> new AttendeeTenantContext(tenantId, trustedActor),
                request -> Uni.createFrom().voidItem(), directUnitOfWork(), Clock.systemUTC());

        var result = handler.handle(new CreateAttendeeProfileCommand(
                partyId, "EVENT_GUEST", "Guest", null, null)).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("ATTENDEE_PROFILE_ALREADY_EXISTS",
                result.failureError().orElseThrow().code());
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
