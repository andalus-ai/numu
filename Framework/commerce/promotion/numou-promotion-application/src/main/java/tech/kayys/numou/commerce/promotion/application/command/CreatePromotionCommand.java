package tech.kayys.syirkah.commerce.promotion.application.command;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingConfiguration;
import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.util.Objects;

/** Creates a draft promotion definition. */
public record CreatePromotionCommand(
        String name,
        int priority,
        DateRange validFor,
        PromotionRule rule,
        PromotionStackingConfiguration stacking
) implements Command {

    public CreatePromotionCommand {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(validFor, "validFor cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
        name = name.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (stacking == null) {
            stacking = PromotionStackingConfiguration.bestResult(priority);
        }
    }

    public CreatePromotionCommand(
            String name,
            int priority,
            DateRange validFor,
            PromotionRule rule
    ) {
        this(name, priority, validFor, rule,
                PromotionStackingConfiguration.bestResult(priority));
    }
}
