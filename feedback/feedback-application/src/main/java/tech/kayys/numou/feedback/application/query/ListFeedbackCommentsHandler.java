package tech.kayys.numou.feedback.application.query;

import tech.kayys.numou.feedback.application.port.FeedbackCommentAccessPolicy;
import tech.kayys.numou.feedback.application.port.FeedbackCommentRepository;
import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.comment.FeedbackComment;

import java.util.List;
import java.util.Objects;

/** Tenant-, actor- and visibility-scoped comment listing with bounded pagination. */
public final class ListFeedbackCommentsHandler {
    private final FeedbackRepository feedbacks;
    private final FeedbackCommentRepository comments;
    private final FeedbackCommentAccessPolicy accessPolicy;

    public ListFeedbackCommentsHandler(FeedbackRepository feedbacks, FeedbackCommentRepository comments,
                                       FeedbackCommentAccessPolicy accessPolicy) {
        this.feedbacks = Objects.requireNonNull(feedbacks, "feedbacks cannot be null");
        this.comments = Objects.requireNonNull(comments, "comments cannot be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy cannot be null");
    }

    public List<FeedbackComment> handle(ListFeedbackCommentsQuery query) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(query.tenantId(), "tenantId is required");
        Objects.requireNonNull(query.feedbackId(), "feedbackId is required");
        Objects.requireNonNull(query.requester(), "requester is required");
        Objects.requireNonNull(query.visibility(), "visibility is required");
        if (query.limit() < 1 || query.limit() > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (query.offset() < 0) throw new IllegalArgumentException("offset cannot be negative");

        accessPolicy.assertCanRead(query.tenantId(), query.feedbackId(),
                query.requester(), query.visibility());
        feedbacks.findById(query.tenantId(), query.feedbackId())
                .orElseThrow(() -> new IllegalArgumentException("Feedback does not exist for this tenant"));
        return List.copyOf(comments.findByFeedback(query.tenantId(), query.feedbackId(),
                query.visibility(), query.limit(), query.offset()));
    }
}