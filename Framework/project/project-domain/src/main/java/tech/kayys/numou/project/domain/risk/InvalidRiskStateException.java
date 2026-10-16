package tech.kayys.syirkah.project.domain.risk;

/**
 * Thrown when a risk (or one of its treatment actions) is asked to make
 * a transition its current status forbids — consistent with
 * InvalidProjectStateException / InvalidTaskStateException.
 */
public class InvalidRiskStateException extends RuntimeException {

    public InvalidRiskStateException(String message) {
        super(message);
    }
}
