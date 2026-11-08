package tech.kayys.numou.attendee.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.UUID;

/**
 * Verifies that an actor in a tenant may reference a canonical Party.
 * Implementations combine Party existence with authoritative tenant/identity
 * relationships and must not disclose whether an inaccessible Party exists.
 */
@FunctionalInterface
public interface AttendeePartyAccessPort {
    Uni<Void> requireReference(TenantId tenantId, UUID actorId, PartyId partyId);
}
