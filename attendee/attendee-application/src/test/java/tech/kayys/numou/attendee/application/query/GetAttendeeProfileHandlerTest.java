package tech.kayys.numou.attendee.application.query;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.attendee.application.port.AttendeeAuthorizationPort;
import tech.kayys.numou.attendee.application.port.AttendeeProfileRepository;
import tech.kayys.numou.attendee.application.port.AttendeeTenantContext;
import tech.kayys.numou.attendee.application.port.AttendeeTenantContextProvider;
import tech.kayys.numou.attendee.domain.AttendeeProfile;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GetAttendeeProfileHandlerTest {

    @Test
    void readsOnlyWithinTrustedTenantAfterAuthorization() {
        var tenant = TenantId.generate();
        var actor = UUID.randomUUID();
        var profileId = UUID.randomUUID();
        var profile = AttendeeProfile.create(profileId, tenant, PartyId.generate(),
                "EVENT_GUEST", "Guest", null, null, Instant.parse("2026-11-06T00:00:00Z"));
        var repositoryCalled = new AtomicBoolean();
        var repository = new AttendeeProfileRepository() {
            @Override
            public Uni<AttendeeProfile> save(AttendeeProfile candidate) {
                return Uni.createFrom().item(candidate);
            }
            @Override
            public Uni<Optional<AttendeeProfile>> findByTenantAndId(TenantId tenantId, UUID requestedId) {
                repositoryCalled.set(true);
                assertEquals(tenant, tenantId);
                assertEquals(profileId, requestedId);
                return Uni.createFrom().item(Optional.of(profile));
            }
            @Override
            public Uni<Optional<AttendeeProfile>> findByTenantPartyAndKind(
                    TenantId tenantId, UUID partyId, String profileKind) {
                return Uni.createFrom().item(Optional.empty());
            }
        };
        var authorization = (AttendeeAuthorizationPort) request -> {
            assertEquals(tenant, request.tenantId());
            assertEquals(actor, request.actorId());
            assertEquals(AttendeeAuthorizationPort.Action.VIEW_PROFILE, request.action());
            return Uni.createFrom().voidItem();
        };
        AttendeeTenantContextProvider context = () -> new AttendeeTenantContext(tenant, actor);

        var handler = new GetAttendeeProfileHandler(repository, context, authorization);
        var result = handler.handle(new GetAttendeeProfileQuery(profileId)).await().indefinitely();

        assertTrue(repositoryCalled.get());
        assertEquals(profile, result.orElseThrow());
    }

    @Test
    void authorizationFailurePreventsRepositoryRead() {
        var repositoryCalled = new AtomicBoolean();
        var repository = new AttendeeProfileRepository() {
            @Override
            public Uni<AttendeeProfile> save(AttendeeProfile profile) {
                return Uni.createFrom().item(profile);
            }
            @Override
            public Uni<Optional<AttendeeProfile>> findByTenantAndId(TenantId tenantId, UUID profileId) {
                repositoryCalled.set(true);
                return Uni.createFrom().item(Optional.empty());
            }
            @Override
            public Uni<Optional<AttendeeProfile>> findByTenantPartyAndKind(
                    TenantId tenantId, UUID partyId, String profileKind) {
                return Uni.createFrom().item(Optional.empty());
            }
        };
        var handler = new GetAttendeeProfileHandler(repository,
                () -> new AttendeeTenantContext(TenantId.generate(), UUID.randomUUID()),
                request -> Uni.createFrom().failure(new SecurityException("forbidden")));

        assertThrows(SecurityException.class, () -> handler.handle(
                new GetAttendeeProfileQuery(UUID.randomUUID())).await().indefinitely());
        assertFalse(repositoryCalled.get());
    }
}
