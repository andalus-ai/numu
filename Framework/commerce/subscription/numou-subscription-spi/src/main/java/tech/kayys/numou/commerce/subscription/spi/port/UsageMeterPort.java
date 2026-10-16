package tech.kayys.syirkah.commerce.subscription.spi.port;

import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.domain.UsageMeter;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Period-scoped usage meters. A meter is keyed by
 * (subscription, period); renewal starts a fresh meter, so quotas
 * reset naturally with the billing cycle.
 */
public interface UsageMeterPort {

    CompletionStage<Optional<UsageMeter>> find(
            SubscriptionId subscriptionId, DateRange period);

    CompletionStage<Void> save(UsageMeter meter);
}
