package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionStatus;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityRegistry;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Compiles a persisted {@link Promotion} into a runtime
 * {@link CompiledPromotion} (product03.md).
 *
 * <p>Compilation validates the structure and stamps a fingerprint for cache
 * invalidation. The compiled rule is produced through the
 * {@link PromotionCapabilityRegistry}, so registered condition and effect
 * capabilities are resolved once and their parameter schemas are enforced;
 * unsupported legacy types are wrapped as typed delegates and still compile.</p>
 */
public final class PromotionCompiler {

    private final PromotionCapabilityRegistry registry;

    public PromotionCompiler() {
        this(PromotionCapabilities.defaults());
    }

    public PromotionCompiler(PromotionCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    public PromotionCompilationResult compile(Promotion promotion) {
        Objects.requireNonNull(promotion, "promotion cannot be null");

        List<PromotionCompilationError> errors = new ArrayList<>();

        if (promotion.name() == null || promotion.name().isBlank()) {
            errors.add(PromotionCompilationError.of(
                    "NAME_REQUIRED", "Promotion name is required", "name"));
        }
        if (promotion.rule() == null) {
            errors.add(PromotionCompilationError.of(
                    "RULE_REQUIRED", "Promotion rule is required", "rule"));
        } else {
            if (promotion.rule().condition() == null) {
                errors.add(PromotionCompilationError.of(
                        "CONDITION_REQUIRED",
                        "Promotion condition is required",
                        "rule.condition"));
            }
            if (promotion.rule().action() == null) {
                errors.add(PromotionCompilationError.of(
                        "ACTION_REQUIRED",
                        "Promotion action is required",
                        "rule.action"));
            }
        }
        if (promotion.stacking() == null) {
            errors.add(PromotionCompilationError.of(
                    "STACKING_REQUIRED",
                    "Stacking configuration is required",
                    "stacking"));
        }
        if (promotion.status() == PromotionStatus.INACTIVE
                || promotion.status() == PromotionStatus.ARCHIVED) {
            errors.add(PromotionCompilationError.of(
                    "INACTIVE_PROMOTION",
                    "Inactive or archived promotions cannot be compiled for activation",
                    "status"));
        }

        if (!errors.isEmpty()) {
            return PromotionCompilationResult.failure(errors);
        }

        List<CompiledPromotionRule> compiledRules = promotion.rules().stream()
                .map(rule -> CompiledPromotionRule.compile(rule, registry))
                .toList();
        String fingerprint = fingerprint(promotion, compiledRules);
        var compiled = new CompiledPromotion(
                promotion,
                compiledRules,
                promotion.stacking(),
                fingerprint);

        return PromotionCompilationResult.success(compiled);
    }

    private static String fingerprint(
            Promotion promotion, List<CompiledPromotionRule> rules) {
        String payload = promotion.id().value()
                + "|" + promotion.name()
                + "|" + promotion.priority()
                + "|" + promotion.stacking().mode()
                + "|" + promotion.stacking().stackingGroup()
                + "|" + promotion.validFor();
        for (CompiledPromotionRule rule : rules) {
            payload += "|" + rule.priority() + "|" + rule.condition() + "|" + rule.effect();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
