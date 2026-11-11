package tech.kayys.numou.socialgraph.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

/** Tenant-scoped, bounded keyset queries over FOLLOW edges. */
public interface SocialGraphRelationshipQueryPort {
    Uni<SocialGraphRelationshipQueryPage> findFollowing(
            TenantId tenantId, PartyId follower, Instant at, String cursor, int limit);

    Uni<SocialGraphRelationshipQueryPage> findFollowers(
            TenantId tenantId, PartyId followed, Instant at, String cursor, int limit);
}
