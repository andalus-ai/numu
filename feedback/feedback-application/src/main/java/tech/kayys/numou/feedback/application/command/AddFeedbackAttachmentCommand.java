package tech.kayys.numou.feedback.application.command;

import java.io.InputStream;
import java.util.UUID;

/** Upload request after host authorization has established tenant and feedback access. */
public record AddFeedbackAttachmentCommand(UUID tenantId, UUID feedbackId, String fileName,
        String mediaType, long sizeBytes, String sha256, InputStream content, long maximumAllowedBytes) {
}