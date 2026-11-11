package tech.kayys.numou.feedback.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackAttachmentTest {
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private FeedbackAttachment attachment() {
        return FeedbackAttachment.register(UUID.randomUUID(), UUID.randomUUID(), FeedbackId.generate(),
                "document-123", "version-1", "screenshot.png", "image/png", 1024, HASH, NOW);
    }

    @Test void startsPendingAndCanBecomeAvailable() {
        FeedbackAttachment value = attachment();
        assertEquals(FeedbackAttachment.Status.PENDING_SCAN, value.status());
        value.markAvailable(NOW.plusSeconds(1));
        assertEquals(FeedbackAttachment.Status.AVAILABLE, value.status());
    }

    @Test void rejectedEvidenceCannotBecomeAvailable() {
        FeedbackAttachment value = attachment();
        value.reject("malware detected", NOW.plusSeconds(1));
        assertEquals(FeedbackAttachment.Status.REJECTED, value.status());
        assertThrows(IllegalStateException.class, () -> value.markAvailable(NOW.plusSeconds(2)));
    }

    @Test void rejectsUnsafeFileNamesAndInvalidChecksums() {
        assertThrows(IllegalArgumentException.class, () -> FeedbackAttachment.register(UUID.randomUUID(),
                UUID.randomUUID(), FeedbackId.generate(), "doc", "version", "../secret.txt",
                "text/plain", 12, HASH, NOW));
        assertThrows(IllegalArgumentException.class, () -> FeedbackAttachment.register(UUID.randomUUID(),
                UUID.randomUUID(), FeedbackId.generate(), "doc", "version", "safe.txt",
                "text/plain", 12, "bad", NOW));
    }

    @Test void deletionIsIdempotent() {
        FeedbackAttachment value = attachment();
        value.delete(NOW.plusSeconds(1));
        value.delete(NOW.plusSeconds(2));
        assertEquals(FeedbackAttachment.Status.DELETED, value.status());
    }
}
