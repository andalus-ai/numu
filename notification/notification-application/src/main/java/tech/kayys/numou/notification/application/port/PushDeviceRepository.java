package tech.kayys.numou.notification.application.port;

import tech.kayys.numou.notification.domain.PushDevice;
import tech.kayys.numou.notification.application.PushDeviceSummary;

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

    /** Secret-free registration result; JDBC implementations should not decrypt the token for this use case. */
    default PushDeviceSummary registerOrRefreshSummary(PushDevice device) {
        return PushDeviceSummary.from(registerOrRefresh(device));
    }

    List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId);

    /**
     * Secret-free management projection. JDBC implementations should override
     * this method with a query that never selects or decrypts provider tokens.
     */
    default List<PushDeviceSummary> findActiveSummaries(UUID tenantId, UUID recipientId) {
        return findActiveByRecipient(tenantId, recipientId).stream()
                .map(PushDeviceSummary::from).toList();
    }

    /** Disables only a device owned by this tenant and recipient. */
    Optional<PushDevice> disable(UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt);

    /**
     * Secret-free unregister result. JDBC implementations should override
     * this method to avoid selecting encrypted token columns at all.
     */
    default Optional<PushDeviceSummary> disableSummary(
            UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
        return disable(tenantId, recipientId, deviceId, disabledAt).map(PushDeviceSummary::from);
    }
}
