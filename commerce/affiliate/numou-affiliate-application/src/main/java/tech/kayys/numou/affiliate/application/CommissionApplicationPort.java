package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.CommissionEntry;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Commission calculation API. Implementations record liabilities only; they do not execute payments. */
public interface CommissionApplicationPort {
    CompletionStage<CommissionEntry> calculateCommission(
            AffiliateCommandContext context,
            UUID programId,
            String sourceType,
            String sourceId,
            String conversionEventId);
}
