package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.ActorId;
import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import java.util.UUID;

/** Host-provided authorization. CMS never treats an identifier or workflow assignment as permission. */
public interface EditorialAuthorizationPort {
    void requireSubmit(TenantId tenantId, ActorId actorId, ContentId contentId);
    void requireReview(TenantId tenantId, ActorId actorId, ContentId contentId);
    void requireComment(TenantId tenantId, ActorId actorId, ContentId contentId);
    void requireReviewerEligible(TenantId tenantId, ActorId actorId, UUID reviewerId);
}
