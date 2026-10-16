package tech.kayys.syirkah.commerce.subscription.application.command;

import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.foundation.application.command.Command;

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
