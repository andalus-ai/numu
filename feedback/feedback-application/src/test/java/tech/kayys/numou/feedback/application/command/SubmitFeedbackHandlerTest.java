package tech.kayys.numou.feedback.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SubmitFeedbackHandlerTest {
    @Test
    void submitsThroughTenantAwareRepositoryPort() {
        AtomicReference<FeedbackItem> saved = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        FeedbackRepository repository = new FeedbackRepository() {
            @Override public void save(FeedbackItem item, String idempotencyKey) {
                saved.set(item);
                key.set(idempotencyKey);
            }
            @Override public Optional<FeedbackItem> findById(UUID tenantId, FeedbackId id) {
                return Optional.empty();
            }
        };
        UUID tenantId = UUID.randomUUID();
        SubmitFeedbackHandler handler = new SubmitFeedbackHandler(repository);
        FeedbackId id = handler.handle(new SubmitFeedbackCommand(tenantId, null, FeedbackType.COMMENT,
                "General feedback", "Useful product.", "erp", "dashboard", null,
                null, " request-1 ", Instant.parse("2026-11-10T12:00:00Z")));

        assertEquals(id, saved.get().id());
        assertEquals(tenantId, saved.get().tenantId());
        assertEquals(FeedbackPriority.NORMAL, saved.get().priority());
        assertEquals("request-1", key.get());
    }

    @Test
    void rejectsMissingTenantAndIdempotencyKeyBeforePersistence() {
        FeedbackRepository repository = new FeedbackRepository() {
            @Override public void save(FeedbackItem item, String key) { fail("must not persist"); }
            @Override public Optional<FeedbackItem> findById(UUID tenantId, FeedbackId id) {
                return Optional.empty();
            }
        };
        SubmitFeedbackHandler handler = new SubmitFeedbackHandler(repository);
        SubmitFeedbackCommand command = new SubmitFeedbackCommand(null, null, FeedbackType.BUG,
                "Bug", "Description", null, null, null, FeedbackPriority.HIGH,
                "key", Instant.parse("2026-11-10T12:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
    }
}