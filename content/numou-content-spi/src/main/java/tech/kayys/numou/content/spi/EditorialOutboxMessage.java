package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EditorialOutboxMessage(TenantId tenantId, UUID eventId, EditorialReviewId reviewId,
        ContentId contentId, int revisionNumber, String eventType, String payload,
        int attempts, Instant createdAt) {
    public EditorialOutboxMessage {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(eventId); Objects.requireNonNull(reviewId);
        Objects.requireNonNull(contentId); Objects.requireNonNull(eventType); Objects.requireNonNull(payload);
        Objects.requireNonNull(createdAt);
        if(revisionNumber<1 || attempts<1) throw new IllegalArgumentException("Invalid revision or delivery attempt");
    }
}
