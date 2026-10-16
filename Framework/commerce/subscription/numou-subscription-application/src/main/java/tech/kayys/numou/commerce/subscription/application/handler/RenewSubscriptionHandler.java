package tech.kayys.syirkah.commerce.subscription.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.subscription.application.SubscriptionErrors;
import tech.kayys.syirkah.commerce.subscription.application.command.RenewSubscriptionCommand;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.spi.port.SubscriptionRepositoryPort;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/**
 * Rolls one subscription's period forward. CANCELLED/EXPIRED
 * subscriptions cannot renew — the domain rejects the transition and
 * it surfaces as INVALID_SUBSCRIPTION_STATE.
 */
public final class RenewSubscriptionHandler
        implements CommandHandler<RenewSubscriptionCommand, Result<SubscriptionId>> {

    private final SubscriptionRepositoryPort subscriptions;
    private final EventPublisher eventPublisher;

    public RenewSubscriptionHandler(
            SubscriptionRepositoryPort subscriptions,
            EventPublisher eventPublisher
    ) {
        this.subscriptions = Objects.requireNonNull(subscriptions);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<SubscriptionId>> handle(RenewSubscriptionCommand command) {
        return Uni.createFrom()
                .completionStage(subscriptions.findById(command.subscriptionId()))
                .onItem()
                .transformToUni(maybeSubscription -> {
                    if (maybeSubscription.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of(
                                        SubscriptionErrors.SUBSCRIPTION_NOT_FOUND,
                                        "Subscription does not exist")));
                    }
                    var subscription = maybeSubscription.get();
                    try {
                        subscription.renew();
                    } catch (RuntimeException ex) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of(
                                        SubscriptionErrors.INVALID_SUBSCRIPTION_STATE,
                                        ex.getMessage())));
                    }
                    return Uni.createFrom()
                            .completionStage(subscriptions.save(subscription))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
