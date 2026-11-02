package tech.kayys.numou.affiliate.spi;

import java.util.concurrent.CompletionStage;

/** Transport-neutral publication port. Implementations must preserve event identity for consumer deduplication. */
@FunctionalInterface
public interface AffiliateEventPublisher {
    CompletionStage<Void> publish(AffiliateEventOutboxStore.ClaimedEvent event);
}
