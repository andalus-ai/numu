
package tech.kayys.syirkah.accounting.application.rule;

import java.util.List;

public interface FinancialRuleEngine {
    <C extends RuleContext> void registerRule(Class<C> contextType, FinancialRule<C> rule);
    <C extends RuleContext> List<RuleResult> evaluate(C context);
    <C extends RuleContext> boolean isBlocked(C context);
}
