package tech.kayys.numou.affiliate.spi;
import tech.kayys.numou.affiliate.domain.AttributionDecision;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
/**
 * Durable idempotency boundary. Enforce uniqueness on (tenant, program, source type, source ID, event ID).
 * Exact retries return the stored decision; if a reused key conflicts with immutable source/policy evidence,
 * fail explicitly rather than silently overwriting or returning a different decision.
 */
public interface AttributionDecisionRepository {
    CompletionStage<AttributionDecision> saveOrGet(AttributionDecision proposed);
    CompletionStage<Optional<AttributionDecision>> findBySourceEvent(
            String tenantId, UUID programId, String sourceType, String sourceId, String conversionEventId);
}
