package tech.kayys.syirkah.commerce.subscription.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.offering.domain.OfferingStatus;
import tech.kayys.syirkah.commerce.offering.spi.port.ProductOfferingRepository;
import tech.kayys.syirkah.commerce.subscription.application.SubscriptionErrors;
import tech.kayys.syirkah.commerce.subscription.application.command.StartSubscriptionCommand;
import tech.kayys.syirkah.commerce.subscription.domain.Subscription;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.spi.port.SubscriptionRepositoryPort;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/**
 * Starts a subscription only against an existing, ACTIVE offering —
 * the same gate the pricing capability applies, so a draft/retired
 * plan can never be subscribed to.
 */
public final class StartSubscriptionHandler
        implements CommandHandler<StartSubscriptionCommand, Result<SubscriptionId>> {

    private final ProductOfferingRepository offerings;
    private final SubscriptionRepositoryPort subscriptions;
    private final EventPublisher eventPublisher;

    public StartSubscriptionHandler(
            ProductOfferingRepository offerings,
            SubscriptionRepositoryPort subscriptions,
            EventPublisher eventPublisher
    ) {
        this.offerings = Objects.requireNonNull(offerings);
        this.subscriptions = Objects.requireNonNull(subscriptions);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<SubscriptionId>> handle(StartSubscriptionCommand command) {
        return Uni.createFrom()
                .completionStage(offerings.findById(command.offeringId()))
                .onItem()
                .transformToUni(maybeOffering -> {
                    if (maybeOffering.isEmpty()) {
                        return failure(SubscriptionErrors.OFFERING_NOT_FOUND,
                                "Offering does not exist");
                    }
                    if (maybeOffering.get().status() != OfferingStatus.ACTIVE) {
                        return failure(SubscriptionErrors.OFFERING_NOT_ACTIVE,
                                "Offering is not active");
                    }
                    var subscription = Subscription.start(
                            SubscriptionId.generate(),
                            command.customerRef(),
                            command.offeringId(),
                            command.quantity(),
                            command.billingCycle(),
                            command.renewalPolicy(),
                            command.startDate());
                    return Uni.createFrom()
                            .completionStage(subscriptions.save(subscription))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }

    private static <T> Uni<Result<T>> failure(String code, String message) {
        return Uni.createFrom().item(Result.failure(ApplicationError.of(code, message)));
    }
}
