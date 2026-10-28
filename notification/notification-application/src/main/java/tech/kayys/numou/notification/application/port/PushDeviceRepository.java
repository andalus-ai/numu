package tech.kayys.numou.notification.application.port;

import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for device registrations. Implementations must enforce
 * tenant/recipient scope and prevent one provider token being silently
 * reassigned across owners.
 */
public interface PushDeviceRepository {
    /**
     * Creates a registration or refreshes the same owner's registration for
     * the same provider token. A token already owned by another recipient
     * must not be reassigned by this operation.
     */
    PushDevice registerOrRefresh(PushDevice device);

    List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId);

    /** Disables only a device owned by this tenant and recipient. */
    Optional<PushDevice> disable(UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt);
}
