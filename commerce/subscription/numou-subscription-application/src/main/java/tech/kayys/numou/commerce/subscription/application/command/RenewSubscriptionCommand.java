package tech.kayys.numou.commerce.subscription.application.command;

import tech.kayys.numou.commerce.subscription.domain.SubscriptionId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/**
 * Roll the current billing period forward one cycle. Serves both
 * AUTO_RENEW (issued by the renewal job) and MANUAL (issued by an
 * operator) subscriptions.
 */
public record RenewSubscriptionCommand(
        SubscriptionId subscriptionId
) implements Command {

    public RenewSubscriptionCommand {
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
    }
}
