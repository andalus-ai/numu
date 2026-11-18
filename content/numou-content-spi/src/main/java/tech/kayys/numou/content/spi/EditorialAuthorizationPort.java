package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.application.ContentCommandContext;
import tech.kayys.numou.content.domain.ContentId;
import java.util.UUID;

/** Host-provided authorization. CMS never treats an identifier or workflow assignment as permission. */
public interface EditorialAuthorizationPort {
    void requireSubmit(ContentCommandContext context, ContentId contentId);
    void requireReview(ContentCommandContext context, ContentId contentId);
    void requireComment(ContentCommandContext context, ContentId contentId);
    void requireReviewerEligible(ContentCommandContext context, UUID reviewerId);
}
