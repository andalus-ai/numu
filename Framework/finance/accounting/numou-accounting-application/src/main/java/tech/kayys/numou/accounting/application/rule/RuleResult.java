
package tech.kayys.syirkah.accounting.application.rule;

public record RuleResult(
        boolean matched,
        String ruleCode,
        String message,
        Severity severity,
        RuleAction action
) {
    public enum Severity {
        INFO, WARNING, ERROR, BLOCKING
    }

    public enum RuleAction {
        NONE, APPROVE, REJECT, REQUIRE_ADDITIONAL_APPROVAL, ROUTE_TO_ESCALATION
    }

    public static RuleResult pass(String ruleCode) {
        return new RuleResult(false, ruleCode, "Rule satisfied", Severity.INFO, RuleAction.NONE);
    }

    public static RuleResult trigger(String ruleCode, String message, Severity severity, RuleAction action) {
        return new RuleResult(true, ruleCode, message, severity, action);
    }
}
