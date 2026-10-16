package tech.kayys.numou.commerce.subscription.spi.port;

import tech.kayys.numou.commerce.subscription.domain.Subscription;
import tech.kayys.numou.commerce.subscription.domain.SubscriptionId;
import tech.kayys.numou.foundation.domain.repository.Repository;

/**
 * Persistence for the Subscription aggregate — reuses the
 * foundation {@link Repository} contract, same as the offering
 * capability.
 */
public interface SubscriptionRepositoryPort
        extends Repository<Subscription, SubscriptionId> {
}
