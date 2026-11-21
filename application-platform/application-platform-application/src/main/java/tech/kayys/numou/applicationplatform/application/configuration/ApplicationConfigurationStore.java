package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
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

    /**
     * Returns revisions in descending revision order. beforeRevision is an
     * exclusive cursor; implementations must enforce tenant scope in storage.
     */
    CompletionStage<HistoryPage> findHistory(
            TenantId tenantId, ApplicationId applicationId, Long beforeRevision, int pageSize);

    CompletionStage<SaveResult> save(
            ApplicationConfigurationSnapshot snapshot, long expectedRevision);

    record HistoryPage(List<ApplicationConfigurationSnapshot> revisions, Long nextBeforeRevision) {
        public HistoryPage {
            revisions = List.copyOf(revisions);
            if (revisions.size() > 100)
                throw new IllegalArgumentException("history page cannot exceed 100 revisions");
            long previous = Long.MAX_VALUE;
            for (ApplicationConfigurationSnapshot revision : revisions) {
                if (revision.revision() >= previous)
                    throw new IllegalArgumentException("history revisions must be strictly descending");
                previous = revision.revision();
            }
            if (nextBeforeRevision != null && nextBeforeRevision < 1)
                throw new IllegalArgumentException("nextBeforeRevision must be positive");
            if (nextBeforeRevision != null && !revisions.isEmpty()
                    && nextBeforeRevision != revisions.get(revisions.size() - 1).revision())
                throw new IllegalArgumentException("cursor must equal the last returned revision");
        }
    }

    record SaveResult(boolean saved, Optional<ApplicationConfigurationSnapshot> current) {
        public SaveResult {
            if (current == null) throw new IllegalArgumentException("current is required");
            if (saved && current.isEmpty())
                throw new IllegalArgumentException("successful save must return the saved snapshot");
        }
    }
}
