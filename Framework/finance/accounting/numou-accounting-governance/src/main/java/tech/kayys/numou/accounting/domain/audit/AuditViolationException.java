package tech.kayys.numou.accounting.domain.audit;

public final class AuditViolationException extends RuntimeException {
    public AuditViolationException(String message) {
        super(message);
    }
}
