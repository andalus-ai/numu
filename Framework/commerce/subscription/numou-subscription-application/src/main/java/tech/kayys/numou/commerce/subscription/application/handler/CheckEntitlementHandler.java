package tech.kayys.syirkah.commerce.subscription.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.subscription.application.SubscriptionErrors;
import tech.kayys.syirkah.commerce.subscription.application.query.CheckEntitlementQuery;
import tech.kayys.syirkah.commerce.subscription.domain.AccessDecision;
import tech.kayys.syirkah.commerce.subscription.domain.EntitlementAccess;
import tech.kayys.syirkah.commerce.subscription.domain.UsageMeter;
import tech.kayys.syirkah.commerce.subscription.spi.port.PlanEntitlementPort;
import tech.kayys.syirkah.commerce.subscription.spi.port.SubscriptionRepositoryPort;
import tech.kayys.syirkah.commerce.subscription.spi.port.UsageMeterPort;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/**
 * Read-only entitlement check: no meter yet means zero consumption,
 * so a missing meter is equivalent to an empty one.
 */
public final class CheckEntitlementHandler
        implements QueryHandler<CheckEntitlementQuery, Result<AccessDecision>> {

    private final SubscriptionRepositoryPort subscriptions;
    private final PlanEntitlementPort entitlements;
    private final UsageMeterPort meters;
    private final EntitlementAccess access = new EntitlementAccess();

    public CheckEntitlementHandler(
            SubscriptionRepositoryPort subscriptions,
            PlanEntitlementPort entitlements,
            UsageMeterPort meters
    ) {
        this.subscriptions = Objects.requireNonNull(subscriptions);
        this.entitlements = Objects.requireNonNull(entitlements);
        this.meters = Objects.requireNonNull(meters);
    }

    @Override
    public Uni<Result<AccessDecision>> handle(CheckEntitlementQuery query) {
        return Uni.createFrom()
                .completionStage(subscriptions.findById(query.subscriptionId()))
                .onItem()
                .transformToUni(maybeSubscription -> {
                    if (maybeSubscription.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of(
                                        SubscriptionErrors.SUBSCRIPTION_NOT_FOUND,
                                        "Subscription does not exist")));
                    }
                    var subscription = maybeSubscription.get();
                    return Uni.createFrom()
                            .completionStage(
                                    entitlements.findEntitlements(subscription.offeringId()))
                            .onItem()
                            .transformToUni(plan -> Uni.createFrom()
                                    .completionStage(meters.find(
                                            subscription.id(), subscription.currentPeriod()))
                                    .map(maybeMeter -> Result.success(access.check(
                                            subscription,
                                            plan,
                                            query.code(),
                                            maybeMeter.orElseGet(() -> new UsageMeter(
                                                    subscription.id(),
                                                    subscription.currentPeriod())),
                                            query.at()))));
                });
    }
}
