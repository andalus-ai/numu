package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.application.port.out.FeedbackAttachmentRepository;
import tech.kayys.numou.feedback.application.port.out.FeedbackEvidenceStore;
import tech.kayys.numou.feedback.domain.FeedbackAttachment;
import tech.kayys.numou.feedback.domain.FeedbackId;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Coordinates tenant-scoped evidence storage and metadata registration. */
public final class AddFeedbackAttachmentHandler {
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4",
            "audio/mpeg", "audio/wav", "audio/ogg", "application/pdf", "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FeedbackRepository feedbackRepository;
    private final FeedbackAttachmentRepository attachmentRepository;
    private final FeedbackEvidenceStore evidenceStore;

    public AddFeedbackAttachmentHandler(FeedbackRepository feedbackRepository,
            FeedbackAttachmentRepository attachmentRepository, FeedbackEvidenceStore evidenceStore) {
        this.feedbackRepository = Objects.requireNonNull(feedbackRepository);
        this.attachmentRepository = Objects.requireNonNull(attachmentRepository);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
    }

    public UUID handle(AddFeedbackAttachmentCommand command, Instant now) throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(now, "now");
        if (command.tenantId() == null || command.feedbackId() == null)
            throw new IllegalArgumentException("tenantId and feedbackId are required");
        if (command.maximumAllowedBytes() <= 0 || command.sizeBytes() <= 0
                || command.sizeBytes() > command.maximumAllowedBytes())
            throw new IllegalArgumentException("Attachment size is outside the configured limit");
        String mediaType = command.mediaType() == null ? "" : command.mediaType().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) throw new IllegalArgumentException("Unsupported attachment media type");
        if (command.sha256() == null || !command.sha256().matches("(?i)[a-f0-9]{64}"))
            throw new IllegalArgumentException("A valid SHA-256 checksum is required");

        FeedbackId feedbackId = FeedbackId.fromString(command.feedbackId().toString());
        if (feedbackRepository.findById(command.tenantId(), feedbackId).isEmpty())
            throw new IllegalArgumentException("Feedback item was not found");

        FeedbackEvidenceStore.StoredEvidence stored = evidenceStore.store(new FeedbackEvidenceStore.Upload(
                command.tenantId().toString(), command.fileName(), mediaType, command.sizeBytes(),
                command.sha256().toLowerCase(Locale.ROOT), command.content()));
        if (stored.sizeBytes() != command.sizeBytes()
                || !stored.sha256().equalsIgnoreCase(command.sha256())
                || !stored.mediaType().equalsIgnoreCase(mediaType)) {
            evidenceStore.delete(stored.documentReference());
            throw new IllegalStateException("Document Store metadata does not match upload request");
        }

        FeedbackAttachment attachment = FeedbackAttachment.register(UUID.randomUUID(), command.tenantId(),
                feedbackId, stored.documentReference(), command.fileName(), mediaType,
                stored.sizeBytes(), stored.sha256(), now);
        try {
            attachmentRepository.save(attachment);
        } catch (RuntimeException persistenceFailure) {
            try { evidenceStore.delete(stored.documentReference()); }
            catch (IOException cleanupFailure) { persistenceFailure.addSuppressed(cleanupFailure); }
            throw persistenceFailure;
        }
        return attachment.id();
    }
}