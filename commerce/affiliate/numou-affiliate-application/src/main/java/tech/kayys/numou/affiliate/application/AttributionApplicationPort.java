package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.AttributionDecision;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Application API for idempotent conversion attribution; implementations must authorize tenant and actor. */
public interface AttributionApplicationPort {
    CompletionStage<AttributionDecision> attributeConversion(
            AffiliateCommandContext context,
            UUID programId,
            String sourceType,
            String sourceId,
            String conversionEventId);
}
