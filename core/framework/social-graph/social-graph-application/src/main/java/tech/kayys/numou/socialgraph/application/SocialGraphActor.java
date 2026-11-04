package tech.kayys.numou.socialgraph.application;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

/**
 * Authenticated actor context supplied by the host security boundary.
 * Never construct this from untrusted request-body tenant or party identifiers.
 */
public record SocialGraphActor(TenantId tenantId, PartyId partyId) {
    public SocialGraphActor {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
    }
}
