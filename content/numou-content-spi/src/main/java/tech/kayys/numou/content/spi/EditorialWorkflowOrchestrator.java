package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;

/** Optional Gamelan adapter port. CMS is authoritative; calls happen after durable outbox commit. */
public interface EditorialWorkflowOrchestrator {
    void start(TenantId tenantId, ContentId contentId, int revisionNumber, EditorialReviewId reviewId);
    void signal(TenantId tenantId, EditorialReviewId reviewId, String eventType, String payload);
    void complete(TenantId tenantId, EditorialReviewId reviewId);
    void cancel(TenantId tenantId, EditorialReviewId reviewId);
}
