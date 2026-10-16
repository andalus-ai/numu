package tech.kayys.syirkah.project.domain.risk;

/**
 * Thrown when an issue is asked to make a transition its current status
 * forbids.
 */
public class InvalidIssueStateException extends RuntimeException {

    public InvalidIssueStateException(String message) {
        super(message);
    }
}
