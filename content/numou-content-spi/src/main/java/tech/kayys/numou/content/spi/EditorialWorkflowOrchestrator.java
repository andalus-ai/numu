package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;

/** Optional Gamelan adapter port. Gamelan schedules and correlates work; CMS remains authoritative. */
public interface EditorialWorkflowOrchestrator {
    void start(TenantId tenantId, ContentId contentId, int revisionNumber, EditorialReviewId reviewId);
    void cancel(TenantId tenantId, EditorialReviewId reviewId);
}
