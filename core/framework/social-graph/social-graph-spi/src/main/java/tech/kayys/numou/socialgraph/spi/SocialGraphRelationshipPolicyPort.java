package tech.kayys.numou.socialgraph.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

/**
 * Authoritative, tenant-scoped relationship policy lookups.
 * BLOCK is symmetric at enforcement time even though its stored edge is directed.
 * MUTE is directional: viewer mutes author.
 */
public interface SocialGraphRelationshipPolicyPort {
    Uni<Boolean> isBlocked(TenantId tenantId, PartyId first, PartyId second, Instant at);
    Uni<Boolean> isMuted(TenantId tenantId, PartyId viewer, PartyId author, Instant at);
}
