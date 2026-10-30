package tech.kayys.numou.support.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.support.application.api.SupportPartyContext;

import java.util.Optional;

/**
 * Resolves an identity-linked Party to its tenant-specific Support participant context.
 *
 * Implementations must verify the Party-to-participant mapping and the Party's
 * active relationship/role in the supplied tenant. PartyId and ParticipantId
 * are deliberately not treated as interchangeable.
 */
public interface SupportPartyParticipantResolver {
    Uni<Optional<SupportPartyContext>> resolve(TenantId tenantId, PartyId partyId);
}
