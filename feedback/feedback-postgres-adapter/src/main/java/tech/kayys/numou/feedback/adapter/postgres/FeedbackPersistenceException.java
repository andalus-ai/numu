package tech.kayys.numou.feedback.adapter.postgres;

public final class FeedbackPersistenceException extends RuntimeException {
    public FeedbackPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}