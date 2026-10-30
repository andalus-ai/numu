package tech.kayys.numou.construction.application.consultancy;

/** A non-transient evidence workflow failure that must not be automatically retried. */
public class ConsultancyEvidencePermanentFailureException extends RuntimeException {
    public ConsultancyEvidencePermanentFailureException(String message) {
        super(message);
    }
}
