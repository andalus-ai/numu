package tech.kayys.syirkah.commerce.promotion.domain.validation;

import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledPromotion;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionCompilationResult;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionCompiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Activation gate (product03.md section 25): condition/effect/target types
 * exist, parameters are valid, rules are structurally valid, date ranges
 * and stacking configuration are valid. Uses the compiler's structural
 * diagnostics, then adds rule-level checks. DateRange itself enforces
 * start &lt;= end, so no separate date check is needed here.
 */
public final class PromotionValidator {

    private final PromotionCompiler compiler;

    public PromotionValidator() {
        this(new PromotionCompiler());
    }

    public PromotionValidator(PromotionCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler);
    }

    public PromotionValidationResult validate(Promotion promotion) {
        Objects.requireNonNull(promotion, "promotion cannot be null");
        List<PromotionValidationError> errors = new ArrayList<>();

        PromotionCompilationResult compiled = compiler.compile(promotion);
        if (!compiled.isSuccess()) {
            for (var error : compiled.errors()) {
                errors.add(PromotionValidationError.of(
                        error.code(), error.message(), error.path()));
            }
        }

        if (promotion.rules() == null || promotion.rules().isEmpty()) {
            errors.add(PromotionValidationError.of(
                    "RULES_REQUIRED", "At least one rule is required", "rules"));
        } else {
            var ids = new HashSet<>();
            for (int index = 0; index < promotion.rules().size(); index++) {
                PromotionRule rule = promotion.rules().get(index);
                String path = "rules[" + index + "]";
                if (rule == null) {
                    errors.add(PromotionValidationError.of(
                            "RULE_REQUIRED", "Rule cannot be null", path));
                    continue;
                }
                if (!ids.add(rule.id())) {
                    errors.add(PromotionValidationError.of(
                            "RULE_DUPLICATE_ID", "Duplicate rule id", path + ".id"));
                }
                if (rule.condition() == null) {
                    errors.add(PromotionValidationError.of(
                            "CONDITION_REQUIRED", "Rule condition is required",
                            path + ".condition"));
                }
                if (rule.action() == null) {
                    errors.add(PromotionValidationError.of(
                            "EFFECT_REQUIRED", "Rule effect is required", path + ".action"));
                }
            }
        }

        if (promotion.stacking() == null) {
            errors.add(PromotionValidationError.of(
                    "STACKING_REQUIRED", "Stacking configuration is required", "stacking"));
        }

        if (errors.isEmpty()) {
            return PromotionValidationResult.ok();
        }
        return PromotionValidationResult.invalid(errors);
    }

    /** Validates an already-compiled promotion (fingerprint preserved). */
    public PromotionValidationResult validate(CompiledPromotion compiled) {
        Objects.requireNonNull(compiled, "compiled cannot be null");
        return validate(compiled.source());
    }
}
