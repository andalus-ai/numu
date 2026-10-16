package tech.kayys.syirkah.commerce.subscription.application.command;

import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Record metered usage against one of the subscription's
 * entitlements in the current period (blueprint §9 "Usage").
 */
public record RecordUsageCommand(
        SubscriptionId subscriptionId,
        String code,
        BigDecimal units,
        Instant at
) implements Command {

    public RecordUsageCommand {
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(units, "units cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        if (units.signum() <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
    }
}
