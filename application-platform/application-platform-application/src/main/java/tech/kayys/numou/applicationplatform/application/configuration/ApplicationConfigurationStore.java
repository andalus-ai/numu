package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-isolated configuration persistence boundary.
 *
 * <p>save must atomically append the immutable revision and advance the current
 * snapshot only when expectedRevision matches. expectedRevision=0 means create
 * only when no configuration exists. A failed compare-and-set must not append
 * history or emit an event.</p>
 */
public interface ApplicationConfigurationStore {
    CompletionStage<Optional<ApplicationConfigurationSnapshot>> findCurrent(
            TenantId tenantId, String applicationId);

    CompletionStage<SaveResult> save(
            ApplicationConfigurationSnapshot snapshot, long expectedRevision);

    record SaveResult(boolean saved, Optional<ApplicationConfigurationSnapshot> current) {
        public SaveResult {
            if (current == null) throw new IllegalArgumentException("current is required");
            if (saved && current.isEmpty())
                throw new IllegalArgumentException("successful save must return the saved snapshot");
        }
    }
}
