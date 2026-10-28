package tech.kayys.numou.affiliate.application;
import tech.kayys.numou.affiliate.domain.AttributionDecision;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
/** Tenant-authorized application API for idempotent conversion attribution. */
public interface AttributionApplicationPort {
    CompletionStage<AttributionDecision> attributeConversion(
            AffiliateCommandContext context, UUID programId, String sourceType, String sourceId, String conversionEventId);
}
