package tech.kayys.syirkah.commerce.promotion.application.command;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

/** Compiles and activates a draft promotion. */
public record ActivatePromotionCommand(
        PromotionId promotionId
) implements Command {

    public ActivatePromotionCommand {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
    }
}
