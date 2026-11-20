package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;
import java.util.UUID;

/** Optional Gamelan adapter port. CMS is authoritative; event IDs provide at-least-once deduplication. */
public interface EditorialWorkflowOrchestrator {
    void start(TenantId tenantId, UUID eventId, ContentId contentId, int revisionNumber, EditorialReviewId reviewId);
    void signal(TenantId tenantId, UUID eventId, EditorialReviewId reviewId, String eventType, String payload);
    void complete(TenantId tenantId, UUID eventId, EditorialReviewId reviewId);
    void cancel(TenantId tenantId, UUID eventId, EditorialReviewId reviewId);
}
