package tech.kayys.numou.feedback.application.port.out;

import tech.kayys.numou.feedback.domain.FeedbackAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped metadata persistence port. Every query must include the tenant predicate.
 * Implementations persist attachment state and its integration outbox event atomically;
 * replaying an unchanged state must not emit a duplicate event.
 */
public interface FeedbackAttachmentRepository {
    void save(FeedbackAttachment attachment);
    Optional<FeedbackAttachment> findById(UUID tenantId, UUID attachmentId);
    List<FeedbackAttachment> findByFeedback(UUID tenantId, UUID feedbackId);
}