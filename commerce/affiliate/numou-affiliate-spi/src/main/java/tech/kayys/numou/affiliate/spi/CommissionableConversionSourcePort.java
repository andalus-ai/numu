package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.CommissionConversion;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Source adapter verifies conversion qualification and provides the eligible monetary base plus
 * the stored attribution decision. Amounts and lifecycle status must come from authoritative source facts.
 */
public interface CommissionableConversionSourcePort {
    CompletionStage<Optional<CommissionConversion>> findCommissionableConversion(
            String tenantId, String sourceType, String sourceId, String conversionEventId);
}
