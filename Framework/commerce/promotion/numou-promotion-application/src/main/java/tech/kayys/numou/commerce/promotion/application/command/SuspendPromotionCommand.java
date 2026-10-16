package tech.kayys.numou.commerce.promotion.application.command;

import tech.kayys.numou.commerce.promotion.domain.PromotionId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/** Suspends (deactivates) an active promotion and invalidates cache. */
public record SuspendPromotionCommand(
        PromotionId promotionId
) implements Command {

    public SuspendPromotionCommand {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
    }
}
