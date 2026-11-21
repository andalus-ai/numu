package tech.kayys.numou.feedback.application.outbox;

/** Runtime adapter that publishes an already committed Feedback integration event. */
@FunctionalInterface
public interface FeedbackEventPublisher {
    void publish(FeedbackOutboxMessage message) throws Exception;
}
