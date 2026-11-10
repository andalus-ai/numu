package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackItem;
import tech.kayys.numou.feedback.domain.FeedbackPriority;

import java.util.Objects;

/** Application use case; authentication and tenant authorization are enforced by the host boundary. */
public final class SubmitFeedbackHandler {
    private final FeedbackRepository repository;

    public SubmitFeedbackHandler(FeedbackRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    public FeedbackId handle(SubmitFeedbackCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        if (command.tenantId() == null) throw new IllegalArgumentException("tenantId is required");
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        FeedbackPriority priority = command.priority() == null
                ? FeedbackPriority.NORMAL : command.priority();
        FeedbackId id = FeedbackId.generate();
        FeedbackItem item = FeedbackItem.submit(id, command.tenantId(), command.submitterId(),
                command.type(), command.title(), command.description(), command.sourceApplication(),
                command.sourceModule(), command.sourceReference(), priority, command.occurredAt());
        repository.save(item, command.idempotencyKey().trim());
        return id;
    }
}