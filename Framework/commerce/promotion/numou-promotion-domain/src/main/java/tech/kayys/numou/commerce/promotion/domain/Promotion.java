package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingConfiguration;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A promotion campaign: rules with a lifecycle, validity window,
 * tie-break priority, tenant scope and stacking configuration
 * (product03.md / product04.md).
 *
 * <p>Immutable; lifecycle transitions return new instances. Only ACTIVE
 * promotions inside their validity window may touch a live cart.</p>
 */
public record Promotion(
        PromotionId id,
        String name,
        PromotionStatus status,
        int priority,
        DateRange validFor,
        List<PromotionRule> rules,
        PromotionStackingConfiguration stacking,
        TenantRef tenantId,
        PromotionScope scope
) {

    public Promotion {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(validFor, "validFor cannot be null");
        Objects.requireNonNull(rules, "rules cannot be null");
        Objects.requireNonNull(stacking, "stacking cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(scope, "scope cannot be null");
        name = name.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules cannot be empty");
        }
        rules = List.copyOf(rules);
    }

    /** Primary / first rule (legacy accessor). */
    public PromotionRule rule() {
        return rules.getFirst();
    }

    public static Promotion draft(
            PromotionId id, String name, int priority,
            DateRange validFor, PromotionRule rule) {
        return draft(id, name, priority, validFor, rule,
                PromotionStackingConfiguration.bestResult(priority),
                TenantRef.of("DEFAULT"), PromotionScope.all());
    }

    public static Promotion draft(
            PromotionId id, String name, int priority,
            DateRange validFor, PromotionRule rule,
            PromotionStackingConfiguration stacking) {
        return draft(id, name, priority, validFor, rule, stacking,
                TenantRef.of("DEFAULT"), PromotionScope.all());
    }

    public static Promotion draft(
            PromotionId id, String name, int priority,
            DateRange validFor, PromotionRule rule,
            PromotionStackingConfiguration stacking,
            TenantRef tenantId, PromotionScope scope) {
        return new Promotion(id, name, PromotionStatus.DRAFT, priority, validFor,
                List.of(rule), stacking, tenantId, scope);
    }

    public static Promotion draft(
            PromotionId id, String name, int priority,
            DateRange validFor, List<PromotionRule> rules,
            PromotionStackingConfiguration stacking,
            TenantRef tenantId, PromotionScope scope) {
        return new Promotion(id, name, PromotionStatus.DRAFT, priority, validFor,
                rules, stacking, tenantId, scope);
    }

    public Promotion activate() {
        return new Promotion(id, name, PromotionStatus.ACTIVE, priority, validFor,
                rules, stacking, tenantId, scope);
    }

    public Promotion deactivate() {
        return new Promotion(id, name, PromotionStatus.INACTIVE, priority, validFor,
                rules, stacking, tenantId, scope);
    }

    public Promotion schedule() {
        return new Promotion(id, name, PromotionStatus.SCHEDULED, priority, validFor,
                rules, stacking, tenantId, scope);
    }

    public Promotion pause() {
        return new Promotion(id, name, PromotionStatus.PAUSED, priority, validFor,
                rules, stacking, tenantId, scope);
    }

    public Promotion withStacking(PromotionStackingConfiguration stacking) {
        return new Promotion(id, name, status, priority, validFor, rules, stacking,
                tenantId, scope);
    }

    public Promotion withScope(PromotionScope scope) {
        return new Promotion(id, name, status, priority, validFor, rules, stacking,
                tenantId, scope);
    }

    public Promotion addRule(PromotionRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        List<PromotionRule> next = new ArrayList<>(rules);
        next.add(rule);
        return new Promotion(id, name, status, priority, validFor, next, stacking,
                tenantId, scope);
    }

    public Promotion replaceRule(PromotionRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        List<PromotionRule> next = new ArrayList<>();
        boolean replaced = false;
        for (var existing : rules) {
            if (existing.id().equals(rule.id())) {
                next.add(rule);
                replaced = true;
            } else {
                next.add(existing);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("rule not found: " + rule.id().value());
        }
        return new Promotion(id, name, status, priority, validFor, next, stacking,
                tenantId, scope);
    }

    public List<PromotionRule> rulesByPriorityDesc() {
        return rules.stream()
                .sorted(Comparator.comparingInt(PromotionRule::priority).reversed())
                .toList();
    }

    public boolean isActiveOn(LocalDate date) {
        return status == PromotionStatus.ACTIVE && validFor.contains(date);
    }

    /** Effective state for the resolver: ACTIVE and within validity window. */
    public boolean isCandidateFor(LocalDate date) {
        return status == PromotionStatus.ACTIVE && validFor.contains(date);
    }
}
