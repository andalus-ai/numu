package tech.kayys.numou.numoudomain;

/** A single reason a syirkah contract is not valid under hukum muamalah. */
public record ContractViolation(String code, String message) {
    @Override
    public String toString() {
        return code + ": " + message;
    }
}
