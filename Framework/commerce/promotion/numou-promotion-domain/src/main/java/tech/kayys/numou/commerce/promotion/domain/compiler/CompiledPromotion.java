package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-ready promotion: conditions/actions are already typed
 * evaluators (product03.md compiler output). For the current model
 * this is a thin typed wrapper around the persisted source, extended with the
 * capability-registry-driven compiled rules and a stability fingerprint.
 */
public record CompiledPromotion(
        Promotion source,
        List<CompiledPromotionRule> rules,
        PromotionStackingConfiguration stacking,
        String fingerprint
) {

    public CompiledPromotion {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(rules, "rules cannot be null");
        Objects.requireNonNull(stacking, "stacking cannot be null");
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
        rules = List.copyOf(rules);
    }

    /**
     * Returns the compiled rules in descending priority order.
     */
    public List<CompiledPromotionRule> rulesByPriorityDesc() {
        if (rules.size() <= 1) {
            return rules;
        }
        return Collections.unmodifiableList(new ArrayList<>(rules)
                .stream()
                .sorted(Comparator.comparingInt(CompiledPromotionRule::priority).reversed())
                .toList());
    }
}
