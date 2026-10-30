package tech.kayys.numou.construction.application.consultancy;

/** Raised by an authorization adapter when an evidence operation is not permitted. */
public class ConsultancyEvidenceAccessDeniedException extends RuntimeException {
    public ConsultancyEvidenceAccessDeniedException(String message) {
        super(message);
    }
}
