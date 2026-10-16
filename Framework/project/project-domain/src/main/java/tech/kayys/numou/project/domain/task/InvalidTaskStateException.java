package tech.kayys.syirkah.project.domain.task;

/**
 * Thrown when a task lifecycle transition is not allowed in the
 * task's current status.
 */
public class InvalidTaskStateException extends RuntimeException {

    public InvalidTaskStateException(String message) {
        super(message);
    }
}
