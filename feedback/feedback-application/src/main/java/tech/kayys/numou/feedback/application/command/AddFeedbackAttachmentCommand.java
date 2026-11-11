package tech.kayys.numou.feedback.application.command;

import java.util.UUID;

/** Associates an already-uploaded, completed Document version with a feedback item. */
public record AddFeedbackAttachmentCommand(
        UUID tenantId,
        UUID feedbackId,
        String documentId,
        String documentVersionId) {
}
