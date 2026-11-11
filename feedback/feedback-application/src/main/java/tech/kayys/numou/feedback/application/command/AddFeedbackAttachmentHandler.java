package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.application.port.out.FeedbackAttachmentRepository;
import tech.kayys.numou.feedback.application.port.out.FeedbackDocumentPort;
import tech.kayys.numou.feedback.domain.FeedbackAttachment;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Links an existing Document version to feedback. Binary upload, scanning, storage and
 * download authorization remain owned by the Document capability.
 */
public final class AddFeedbackAttachmentHandler {
    private final FeedbackRepository feedbackRepository;
    private final FeedbackAttachmentRepository attachmentRepository;
    private final FeedbackDocumentPort documents;

    public AddFeedbackAttachmentHandler(FeedbackRepository feedbackRepository,
            FeedbackAttachmentRepository attachmentRepository, FeedbackDocumentPort documents) {
        this.feedbackRepository = Objects.requireNonNull(feedbackRepository);
        this.attachmentRepository = Objects.requireNonNull(attachmentRepository);
        this.documents = Objects.requireNonNull(documents);
    }

    public UUID handle(AddFeedbackAttachmentCommand command, Instant now) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(now, "now");
        if (command.tenantId() == null || command.feedbackId() == null)
            throw new IllegalArgumentException("tenantId and feedbackId are required");
        if (command.documentId() == null || command.documentId().isBlank()
                || command.documentVersionId() == null || command.documentVersionId().isBlank())
            throw new IllegalArgumentException("documentId and documentVersionId are required");

        FeedbackId feedbackId = FeedbackId.fromString(command.feedbackId().toString());
        if (feedbackRepository.findById(TenantId.of(command.tenantId()), feedbackId).isEmpty())
            throw new IllegalArgumentException("Feedback item was not found");

        var evidence = documents.findVersion(command.tenantId(), command.documentId(), command.documentVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Document version was not found for this tenant"));
        if (!command.documentId().equals(evidence.documentId())
                || !command.documentVersionId().equals(evidence.documentVersionId()))
            throw new IllegalStateException("Document adapter returned a mismatched document version");
        if (!"ACTIVE".equals(evidence.lifecycleStatus()) && !"PUBLISHED".equals(evidence.lifecycleStatus()))
            throw new IllegalArgumentException("Only active or published documents can be attached");
        if (evidence.sizeBytes() <= 0 || evidence.fileName() == null || evidence.mediaType() == null
                || evidence.sha256() == null || !evidence.sha256().matches("(?i)[a-f0-9]{64}"))
            throw new IllegalStateException("Document capability returned invalid evidence metadata");

        FeedbackAttachment attachment = FeedbackAttachment.register(UUID.randomUUID(), command.tenantId(),
                feedbackId, evidence.documentId(), evidence.documentVersionId(), evidence.fileName(),
                evidence.mediaType(), evidence.sizeBytes(), evidence.sha256(), now);
        attachmentRepository.save(attachment);
        return attachment.id();
    }
}
