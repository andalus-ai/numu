package tech.kayys.numou.commerce.promotion.application.command;

import tech.kayys.numou.commerce.promotion.domain.PromotionId;
import tech.kayys.numou.commerce.promotion.domain.PromotionRule;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record UpdateRuleCommand(
        PromotionId promotionId,
        PromotionRule rule
) implements Command {

    public UpdateRuleCommand {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
    }
}
