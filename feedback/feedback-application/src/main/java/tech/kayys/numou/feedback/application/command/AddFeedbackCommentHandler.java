package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.FeedbackCommentAccessPolicy;
import tech.kayys.numou.feedback.application.port.FeedbackCommentRepository;
import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.comment.FeedbackComment;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentId;

import java.util.Objects;

/** Validates host authorization, feedback and parent scope before appending a discussion entry. */
public final class AddFeedbackCommentHandler {
    private final FeedbackRepository feedbacks;
    private final FeedbackCommentRepository comments;
    private final FeedbackCommentAccessPolicy accessPolicy;

    public AddFeedbackCommentHandler(FeedbackRepository feedbacks, FeedbackCommentRepository comments,
                                     FeedbackCommentAccessPolicy accessPolicy) {
        this.feedbacks = Objects.requireNonNull(feedbacks, "feedbacks cannot be null");
        this.comments = Objects.requireNonNull(comments, "comments cannot be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy cannot be null");
    }

    public FeedbackCommentId handle(AddFeedbackCommentCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        Objects.requireNonNull(command.feedbackId(), "feedbackId is required");
        Objects.requireNonNull(command.author(), "author is required");
        Objects.requireNonNull(command.visibility(), "visibility is required");
        Objects.requireNonNull(command.createdAt(), "createdAt is required");
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().trim().length() > 200) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..200 characters");
        }

        accessPolicy.assertCanAdd(command.tenantId(), command.feedbackId(),
                command.author(), command.visibility());
        feedbacks.findById(command.tenantId(), command.feedbackId())
                .orElseThrow(() -> new IllegalArgumentException("Feedback does not exist for this tenant"));

        if (command.parentCommentId() != null) {
            var parent = comments.findById(command.tenantId(), command.parentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment does not exist for this tenant"));
            if (!parent.feedbackId().equals(command.feedbackId())) {
                throw new IllegalArgumentException("Parent comment belongs to another feedback item");
            }
            if (parent.visibility() != command.visibility()) {
                throw new IllegalArgumentException("A reply must use the parent's visibility");
            }
            if (command.createdAt().isBefore(parent.createdAt())) {
                throw new IllegalArgumentException("A reply cannot predate its parent comment");
            }
        }

        FeedbackComment comment = FeedbackComment.add(FeedbackCommentId.generate(),
                command.tenantId(), command.feedbackId(), command.parentCommentId(),
                command.author(), command.visibility(), command.body(), command.createdAt());
        return comments.save(comment, command.idempotencyKey().trim());
    }
}