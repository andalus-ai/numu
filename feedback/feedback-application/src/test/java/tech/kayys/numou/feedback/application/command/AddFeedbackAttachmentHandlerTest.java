package tech.kayys.numou.feedback.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.application.port.out.FeedbackAttachmentRepository;
import tech.kayys.numou.feedback.application.port.out.FeedbackDocumentPort;
import tech.kayys.numou.feedback.domain.FeedbackAttachment;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackItem;
import tech.kayys.numou.feedback.domain.FeedbackPriority;
import tech.kayys.numou.feedback.domain.FeedbackType;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AddFeedbackAttachmentHandlerTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final FeedbackId FEEDBACK_ID = FeedbackId.generate();
    private static final String DOCUMENT_ID = "document-123";
    private static final String VERSION_ID = "version-1";
    private static final Instant NOW = Instant.parse("2026-11-11T04:00:00Z");
    private static final String HASH = "b".repeat(64);

    private FeedbackRepository feedbackRepository() {
        FeedbackItem item = FeedbackItem.submit(FEEDBACK_ID, TENANT, UUID.randomUUID(),
                FeedbackType.BUG, "Upload fails", "The upload fails intermittently",
                "portal", "feedback", null, FeedbackPriority.NORMAL, NOW);
        return new FeedbackRepository() {
            @Override public FeedbackId save(FeedbackItem feedback, String idempotencyKey) { return feedback.id(); }
            @Override public Optional<FeedbackItem> findById(UUID tenantId, FeedbackId id) {
                return TENANT.equals(tenantId) && FEEDBACK_ID.equals(id) ? Optional.of(item) : Optional.empty();
            }
        };
    }

    @Test void attachesAnExactDocumentVersionWithoutCopyingBinaryContent() {
        List<FeedbackAttachment> saved = new ArrayList<>();
        FeedbackAttachmentRepository attachments = new FeedbackAttachmentRepository() {
            @Override public void save(FeedbackAttachment attachment) { saved.add(attachment); }
            @Override public Optional<FeedbackAttachment> findById(UUID tenantId, UUID id) { return Optional.empty(); }
            @Override public List<FeedbackAttachment> findByFeedback(UUID tenantId, UUID feedbackId) { return List.copyOf(saved); }
        };
        FeedbackDocumentPort documents = (tenant, documentId, versionId) ->
                Optional.of(new FeedbackDocumentPort.DocumentEvidence(documentId, versionId,
                        "error.png", "image/png", 512, HASH, "ACTIVE"));

        var handler = new AddFeedbackAttachmentHandler(feedbackRepository(), attachments, documents);
        UUID attachmentId = handler.handle(new AddFeedbackAttachmentCommand(
                TENANT, FEEDBACK_ID.value(), DOCUMENT_ID, VERSION_ID), NOW);

        assertEquals(1, saved.size());
        assertEquals(attachmentId, saved.get(0).id());
        assertEquals(DOCUMENT_ID, saved.get(0).documentId());
        assertEquals(VERSION_ID, saved.get(0).documentVersionId());
        assertEquals(FeedbackAttachment.Status.PENDING_SCAN, saved.get(0).status());
    }

    @Test void rejectsDocumentsNotFoundWithinTheCurrentTenant() {
        FeedbackAttachmentRepository attachments = new FeedbackAttachmentRepository() {
            @Override public void save(FeedbackAttachment attachment) { fail("must not persist"); }
            @Override public Optional<FeedbackAttachment> findById(UUID tenantId, UUID id) { return Optional.empty(); }
            @Override public List<FeedbackAttachment> findByFeedback(UUID tenantId, UUID id) { return List.of(); }
        };
        FeedbackDocumentPort documents = (tenant, documentId, versionId) -> Optional.empty();
        var handler = new AddFeedbackAttachmentHandler(feedbackRepository(), attachments, documents);

        assertThrows(IllegalArgumentException.class, () -> handler.handle(
                new AddFeedbackAttachmentCommand(TENANT, FEEDBACK_ID.value(), DOCUMENT_ID, VERSION_ID), NOW));
    }

    @Test void rejectsArchivedOrDeletedDocuments() {
        FeedbackAttachmentRepository attachments = new FeedbackAttachmentRepository() {
            @Override public void save(FeedbackAttachment attachment) { fail("must not persist"); }
            @Override public Optional<FeedbackAttachment> findById(UUID tenantId, UUID id) { return Optional.empty(); }
            @Override public List<FeedbackAttachment> findByFeedback(UUID tenantId, UUID id) { return List.of(); }
        };
        FeedbackDocumentPort documents = (tenant, documentId, versionId) ->
                Optional.of(new FeedbackDocumentPort.DocumentEvidence(documentId, versionId,
                        "error.png", "image/png", 512, HASH, "ARCHIVED"));
        var handler = new AddFeedbackAttachmentHandler(feedbackRepository(), attachments, documents);

        assertThrows(IllegalArgumentException.class, () -> handler.handle(
                new AddFeedbackAttachmentCommand(TENANT, FEEDBACK_ID.value(), DOCUMENT_ID, VERSION_ID), NOW));
    }
}
