package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.Feedback;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackPriority;

import java.util.Objects;

/** Application use case; host authentication and tenant authorization precede invocation. */
public final class SubmitFeedbackHandler {
    private final FeedbackRepository repository;

    public SubmitFeedbackHandler(FeedbackRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    public FeedbackId handle(SubmitFeedbackCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        Objects.requireNonNull(command.author(), "author is required");
        Objects.requireNonNull(command.kind(), "kind is required");
        Objects.requireNonNull(command.occurredAt(), "occurredAt is required");
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        Feedback feedback = Feedback.submit(FeedbackId.generate(), command.tenantId(),
                command.author(), command.kind(),
                command.priority() == null ? FeedbackPriority.NORMAL : command.priority(),
                command.title(), command.description(), command.context(), command.occurredAt());
        return repository.save(feedback, command.idempotencyKey().trim());
    }
}