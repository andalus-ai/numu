package tech.kayys.numou.socialgraph.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * Host-owned profile visibility/consent decision.
 * Implementations must fail closed and evaluate ownership, profile visibility,
 * consent, moderation and tenant-sharing policy. Social Graph does not infer
 * visibility from the existence of a FOLLOW edge.
 */
public interface SocialGraphRelationshipVisibilityPort {
    Uni<Boolean> canViewProfile(TenantId tenantId, PartyId viewer, PartyId profileOwner);
}
