package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.StayQuote;
import tech.kayys.numou.hospitality.domain.StaySearch;

/** Calculates and snapshots rates, taxes, fees, and cancellation-policy version. */
public interface StayQuotePolicy {
    Uni<StayQuote> quote(String tenantId, StaySearch search, String currency, UUID quoteId);
}
