package tech.kayys.syirkah.accounting.domain.hardening;

/** Thrown when Segregation of Duties policy is violated (e.g. maker approves own transaction). */
public class SoDViolationException extends RuntimeException {
    public SoDViolationException(String message) { super(message); }
}
