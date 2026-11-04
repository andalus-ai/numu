package tech.kayys.numou.applicationplatform.application.installation;

/** Typed provisioning failure; only stable codes are persisted, never raw exception messages. */
public final class ApplicationInstallationProvisioningException extends RuntimeException {
    private final String failureCode;
    private final boolean retryable;

    public ApplicationInstallationProvisioningException(String failureCode, boolean retryable, String message) {
        super(message);
        this.failureCode = validate(failureCode);
        this.retryable = retryable;
    }

    public ApplicationInstallationProvisioningException(String failureCode, boolean retryable,
            String message, Throwable cause) {
        super(message, cause);
        this.failureCode = validate(failureCode);
        this.retryable = retryable;
    }

    public String failureCode() { return failureCode; }
    public boolean retryable() { return retryable; }

    private static String validate(String value) {
        if (value == null || !value.matches("^[A-Z][A-Z0-9_]{1,119}$"))
            throw new IllegalArgumentException("failureCode must be a stable uppercase code");
        return value;
    }
}
