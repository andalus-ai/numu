package tech.kayys.numou.construction.application.consultancy;

/** Raised when an actor is not permitted to attach or inspect consultancy evidence. */
public class ConsultancyEvidenceAccessDeniedException extends RuntimeException {
    public ConsultancyEvidenceAccessDeniedException(String message) {
        super(message);
    }
}
