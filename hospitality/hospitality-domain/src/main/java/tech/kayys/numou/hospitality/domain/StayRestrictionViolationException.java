package tech.kayys.numou.hospitality.domain;

/** Stable domain failure code for a stay rejected by date-specific selling rules. */
public final class StayRestrictionViolationException extends IllegalArgumentException {
    private final String code;

    public StayRestrictionViolationException(String code) {
        super("Stay violates restriction: " + code);
        this.code = code;
    }

    public String code() { return code; }
}
