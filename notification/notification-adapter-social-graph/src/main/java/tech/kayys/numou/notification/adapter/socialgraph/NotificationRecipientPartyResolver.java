package tech.kayys.numou.notification.adapter.socialgraph;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Host-owned tenant-scoped identity bridge. Both recipient identity and the
 * attributed source Party must resolve to active canonical Parties in the
 * notification tenant. UUID equality is not an identity mapping.
 */
public interface NotificationRecipientPartyResolver {
    Optional<PartyId> resolveRecipient(TenantId tenantId, UUID notificationRecipientId);

    /** Validates that the attributed source Party exists and is active in this tenant. */
    Optional<PartyId> resolveSourceActor(TenantId tenantId, UUID canonicalSourcePartyId);
}
