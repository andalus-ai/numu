package tech.kayys.syirkah.accounting.application.rule;

/** Thrown when a blocking rule violation occurs. */
public class RuleViolationException extends RuntimeException {
    private final String ruleCode;

    public RuleViolationException(String ruleCode, String message) {
        super("Rule violation [" + ruleCode + "]: " + message);
        this.ruleCode = ruleCode;
    }

    public String ruleCode() { return ruleCode; }
}
