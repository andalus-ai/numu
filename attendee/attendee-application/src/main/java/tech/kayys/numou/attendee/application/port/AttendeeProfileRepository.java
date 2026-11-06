package tech.kayys.numou.attendee.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.attendee.domain.AttendeeProfile;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for attendee profiles.
 *
 * <p>Every lookup is explicitly tenant-scoped. Implementations must include
 * tenant_id in the database predicate and must enforce uniqueness of
 * (tenant_id, party_id, profile_kind) at the storage boundary.
 */
public interface AttendeeProfileRepository {

    Uni<AttendeeProfile> save(AttendeeProfile profile);

    Uni<Optional<AttendeeProfile>> findByTenantAndId(TenantId tenantId, UUID profileId);

    Uni<Optional<AttendeeProfile>> findByTenantPartyAndKind(
            TenantId tenantId, UUID partyId, String profileKind);
}
