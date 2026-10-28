package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.ConversionReference;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Adapter boundary for a source domain (e.g. marketplace, booking, subscription, service order).
 * Only returns a conversion after the source has verified its qualifying business lifecycle.
 * Browser redirects, client-supplied status, and payment timeouts are not authoritative evidence.
 */
public interface QualifiedConversionSourcePort {
    CompletionStage<Optional<ConversionReference>> findQualifiedConversion(
            String tenantId, String sourceType, String sourceId, String conversionEventId);
}
