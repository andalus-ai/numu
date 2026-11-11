package tech.kayys.numou.feedback.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Feedback-owned evidence association. The referenced binary and its version remain
 * owned and served by the Document capability.
 */
public final class FeedbackAttachment {
    public enum Status { PENDING_SCAN, AVAILABLE, REJECTED, DELETED }

    private final UUID id;
    private final UUID tenantId;
    private final FeedbackId feedbackId;
    private final String documentId;
    private final String documentVersionId;
    private final String fileName;
    private final String mediaType;
    private final long sizeBytes;
    private final String sha256;
    private final Instant createdAt;
    private Status status;
    private Instant updatedAt;
    private String rejectionReason;

    private FeedbackAttachment(UUID id, UUID tenantId, FeedbackId feedbackId,
            String documentId, String documentVersionId, String fileName, String mediaType,
            long sizeBytes, String sha256, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.feedbackId = Objects.requireNonNull(feedbackId, "feedbackId");
        this.documentId = identifier(documentId, "documentId");
        this.documentVersionId = identifier(documentVersionId, "documentVersionId");
        this.fileName = fileName(fileName);
        this.mediaType = text(mediaType, "mediaType", 160).toLowerCase(Locale.ROOT);
        if (sizeBytes <= 0) throw new IllegalArgumentException("sizeBytes must be positive");
        this.sizeBytes = sizeBytes;
        this.sha256 = checksum(sha256);
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
        this.status = Status.PENDING_SCAN;
    }

    public static FeedbackAttachment register(UUID id, UUID tenantId, FeedbackId feedbackId,
            String documentId, String documentVersionId, String fileName, String mediaType,
            long sizeBytes, String sha256, Instant now) {
        return new FeedbackAttachment(id, tenantId, feedbackId, documentId, documentVersionId,
                fileName, mediaType, sizeBytes, sha256, now);
    }

    public void markAvailable(Instant now) { transition(Status.AVAILABLE, now, null); }
    public void reject(String reason, Instant now) { transition(Status.REJECTED, now, text(reason, "reason", 500)); }

    public void delete(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == Status.DELETED) return;
        ensureNotBefore(now);
        status = Status.DELETED;
        updatedAt = now;
    }

    private void transition(Status target, Instant now, String reason) {
        Objects.requireNonNull(now, "now");
        if (status != Status.PENDING_SCAN) throw new IllegalStateException("Attachment is not pending scan");
        ensureNotBefore(now);
        status = target;
        rejectionReason = reason;
        updatedAt = now;
    }

    private void ensureNotBefore(Instant now) {
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("now cannot be before updatedAt");
    }
    private static String identifier(String value, String field) {
        String id = text(value, field, 128);
        if (!id.matches("[A-Za-z0-9._:-]+")) throw new IllegalArgumentException(field + " has an invalid format");
        return id;
    }
    private static String fileName(String value) {
        String name = text(value, "fileName", 255);
        if (name.equals(".") || name.equals("..") || name.contains("/") || name.indexOf(92) >= 0
                || name.indexOf('\\0') >= 0) throw new IllegalArgumentException("fileName must be a simple file name");
        return name;
    }
    private static String checksum(String value) {
        String hash = text(value, "sha256", 64).toLowerCase(Locale.ROOT);
        if (!hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("sha256 must be 64 hexadecimal characters");
        return hash;
    }
    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max)
            throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
        return normalized;
    }
    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public FeedbackId feedbackId() { return feedbackId; }
    public String documentId() { return documentId; }
    public String documentVersionId() { return documentVersionId; }
    public String fileName() { return fileName; }
    public String mediaType() { return mediaType; }
    public long sizeBytes() { return sizeBytes; }
    public String sha256() { return sha256; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String rejectionReason() { return rejectionReason; }
}
