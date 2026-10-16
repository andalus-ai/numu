
package tech.kayys.syirkah.accounting.application.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultFinancialRuleEngine implements FinancialRuleEngine {

    private final Map<Class<?>, List<FinancialRule<?>>> rulesByContext = new ConcurrentHashMap<>();

    @Override
    public <C extends RuleContext> void registerRule(Class<C> contextType, FinancialRule<C> rule) {
        rulesByContext.computeIfAbsent(contextType, k -> new ArrayList<>()).add(rule);
        rulesByContext.get(contextType).sort(Comparator.comparingInt(FinancialRule::priority));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends RuleContext> List<RuleResult> evaluate(C context) {
        List<FinancialRule<?>> rules = rulesByContext.get(context.getClass());
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<RuleResult> results = new ArrayList<>();
        for (FinancialRule<?> r : rules) {
            FinancialRule<C> rule = (FinancialRule<C>) r;
            RuleResult result = rule.evaluate(context);
            results.add(result);
            // Stop early if blocking rule triggered
            if (result.matched() && result.severity() == RuleResult.Severity.BLOCKING) {
                break;
            }
        }
        return results;
    }

    @Override
    public <C extends RuleContext> boolean isBlocked(C context) {
        return evaluate(context).stream()
                .anyMatch(r -> r.matched() && r.severity() == RuleResult.Severity.BLOCKING);
    }
}
