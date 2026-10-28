package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.AttributionDecision;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Durable idempotency boundary. Implementations must atomically enforce uniqueness for
 * (tenant, sourceType, sourceId, conversionEventId) and return the prior decision on exact replay.
 */
public interface AttributionDecisionRepository {
    CompletionStage<AttributionDecision> saveOrGet(AttributionDecision proposed);
    CompletionStage<Optional<AttributionDecision>> findBySourceEvent(
            String tenantId, String sourceType, String sourceId, String conversionEventId);
}
