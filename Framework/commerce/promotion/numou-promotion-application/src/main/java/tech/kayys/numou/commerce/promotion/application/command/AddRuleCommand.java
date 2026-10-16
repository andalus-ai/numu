package tech.kayys.syirkah.commerce.promotion.application.command;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

public record AddRuleCommand(
        PromotionId promotionId,
        PromotionRule rule
) implements Command {

    public AddRuleCommand {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
    }
}
