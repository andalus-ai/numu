package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.editorial.EditorialReview;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;
import java.util.Optional;

public interface EditorialReviewRepository {
    void create(EditorialReview review);
    Optional<EditorialReview> find(TenantId tenantId, EditorialReviewId reviewId);
    Optional<EditorialReview> findActiveForRevision(TenantId tenantId, ContentId contentId, int revisionNumber);
    /** Atomic tenant-scoped compare-and-set. expectedVersion is the version read by the caller. */
    boolean update(EditorialReview review, long expectedVersion);
}
