package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.editorial.EditorialReview;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;
import java.util.Optional;

public interface EditorialReviewRepository {
    /** Atomically moves the exact draft revision to IN_REVIEW and persists review + orchestration outbox. */
    void submit(EditorialReview review);
    Optional<EditorialReview> find(TenantId tenantId, EditorialReviewId reviewId);
    Optional<EditorialReview> findActiveForRevision(TenantId tenantId, ContentId contentId, int revisionNumber);
    /** Atomically persists the aggregate delta, mirrors terminal state to content_entry, and appends outbox. */
    boolean update(EditorialReview review, long expectedVersion);
}
