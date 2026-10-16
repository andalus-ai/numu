package tech.kayys.syirkah.accounting.application.rule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Declarative rule evaluator evaluating declarative {@link RuleDefinition} rules.
 */
public final class DeclarativeRuleEngine {

    private final Map<RuleCode, RuleDefinition> registry = new ConcurrentHashMap<>();

    public void register(RuleDefinition def) {
        registry.put(def.code(), def);
    }

    public List<RuleResult> evaluate(Map<String, Object> facts) {
        List<RuleResult> results = new ArrayList<>();
        for (RuleDefinition def : registry.values()) {
            if (!def.isApplicable()) continue;

            Object val = facts.get(def.contextProperty());
            if (val == null) continue;

            boolean matches = false;
            if (val instanceof BigDecimal num && def.threshold() != null) {
                int cmp = num.compareTo(def.threshold());
                matches = switch (def.operator()) {
                    case ">"  -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    case "<"  -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    case "==" -> cmp == 0;
                    default   -> false;
                };
            }

            if (matches) {
                results.add(RuleResult.trigger(
                        def.code().value(),
                        "Rule " + def.name() + " triggered (" + def.contextProperty() + " " + def.operator() + " " + def.threshold() + ")",
                        def.severity(),
                        def.action()
                ));
            } else {
                results.add(RuleResult.pass(def.code().value()));
            }
        }
        return results;
    }

    public void enforce(Map<String, Object> facts) {
        List<RuleResult> results = evaluate(facts);
        for (RuleResult r : results) {
            if (r.matched() && r.severity() == RuleResult.Severity.BLOCKING) {
                throw new RuleViolationException(r.ruleCode(), r.message());
            }
        }
    }
}
