package tech.kayys.numou.notification.adapter.socialgraph;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Host-owned mapping from the Notification recipient identity to the canonical
 * Ecosystem Party. Implementations must resolve an active, tenant-scoped
 * association; UUID equality is not an identity mapping.
 */
@FunctionalInterface
public interface NotificationRecipientPartyResolver {
    Optional<PartyId> resolve(TenantId tenantId, UUID notificationRecipientId);
}
