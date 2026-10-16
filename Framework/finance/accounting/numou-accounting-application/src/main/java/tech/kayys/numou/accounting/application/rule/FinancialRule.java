
package tech.kayys.syirkah.accounting.application.rule;

/**
 * Contract for a single declarative financial rule.
 */
public interface FinancialRule<C extends RuleContext> {
    String code();
    String description();
    int priority();
    RuleResult evaluate(C context);
}
