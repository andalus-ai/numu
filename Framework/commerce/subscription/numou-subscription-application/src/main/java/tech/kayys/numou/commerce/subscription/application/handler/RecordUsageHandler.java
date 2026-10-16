package tech.kayys.syirkah.commerce.subscription.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.subscription.application.SubscriptionErrors;
import tech.kayys.syirkah.commerce.subscription.application.UsageReceipt;
import tech.kayys.syirkah.commerce.subscription.application.command.RecordUsageCommand;
import tech.kayys.syirkah.commerce.subscription.domain.UsageEntry;
import tech.kayys.syirkah.commerce.subscription.domain.UsageMeter;
import tech.kayys.syirkah.commerce.subscription.spi.port.PlanEntitlementPort;
import tech.kayys.syirkah.commerce.subscription.spi.port.SubscriptionRepositoryPort;
import tech.kayys.syirkah.commerce.subscription.spi.port.UsageMeterPort;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Records one metered usage entry against the subscription's current
 * period. Fails when the subscription is not in force on the date,
 * the plan grants no such entitlement, or the period quota would be
 * exceeded — the quota check itself lives in the domain.
 */
public final class RecordUsageHandler
        implements CommandHandler<RecordUsageCommand, Result<UsageReceipt>> {

    private final SubscriptionRepositoryPort subscriptions;
    private final PlanEntitlementPort entitlements;
    private final UsageMeterPort meters;

    public RecordUsageHandler(
            SubscriptionRepositoryPort subscriptions,
            PlanEntitlementPort entitlements,
            UsageMeterPort meters
    ) {
        this.subscriptions = Objects.requireNonNull(subscriptions);
        this.entitlements = Objects.requireNonNull(entitlements);
        this.meters = Objects.requireNonNull(meters);
    }

    @Override
    public Uni<Result<UsageReceipt>> handle(RecordUsageCommand command) {
        return Uni.createFrom()
                .completionStage(subscriptions.findById(command.subscriptionId()))
                .onItem()
                .transformToUni(maybeSubscription -> {
                    if (maybeSubscription.isEmpty()) {
                        return failure(SubscriptionErrors.SUBSCRIPTION_NOT_FOUND,
                                "Subscription does not exist");
                    }
                    var subscription = maybeSubscription.get();
                    var date = command.at().atZone(ZoneOffset.UTC).toLocalDate();
                    if (!subscription.isActiveOn(date)) {
                        return failure(SubscriptionErrors.SUBSCRIPTION_NOT_IN_FORCE,
                                "Subscription is not in force on " + date);
                    }
                    return Uni.createFrom()
                            .completionStage(
                                    entitlements.findEntitlements(subscription.offeringId()))
                            .onItem()
                            .transformToUni(plan -> {
                                var matching = plan.stream()
                                        .filter(e -> e.code().equals(command.code()))
                                        .findFirst();
                                if (matching.isEmpty()) {
                                    return failure(SubscriptionErrors.NOT_ENTITLED,
                                            "Plan grants no entitlement " + command.code());
                                }
                                return accumulate(command, subscription, matching.get());
                            });
                });
    }

    private Uni<Result<UsageReceipt>> accumulate(
            RecordUsageCommand command,
            tech.kayys.syirkah.commerce.subscription.domain.Subscription subscription,
            tech.kayys.syirkah.commerce.subscription.domain.Entitlement entitlement
    ) {
        var period = subscription.currentPeriod();
        return Uni.createFrom()
                .completionStage(meters.find(subscription.id(), period))
                .onItem()
                .transformToUni(maybeMeter -> {
                    var meter = maybeMeter.orElseGet(
                            () -> new UsageMeter(subscription.id(), period));
                    try {
                        meter.record(new UsageEntry(
                                command.code(), command.units(), command.at()), entitlement);
                    } catch (IllegalArgumentException ex) {
                        return failure(SubscriptionErrors.QUOTA_EXCEEDED, ex.getMessage());
                    }
                    return Uni.createFrom()
                            .completionStage(meters.save(meter))
                            .onItem()
                            .transformToUni(ignored -> Uni.createFrom().item(
                                    Result.success(new UsageReceipt(
                                            command.code(),
                                            meter.consumedOf(command.code()),
                                            meter.remaining(entitlement)))));
                });
    }

    private static <T> Uni<Result<T>> failure(String code, String message) {
        return Uni.createFrom().item(Result.failure(ApplicationError.of(code, message)));
    }
}
