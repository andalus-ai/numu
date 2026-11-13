package tech.kayys.numou.feedback.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SubmitFeedbackHandlerTest {
    @Test
    void submitsCanonicalAggregateThroughTenantAwareRepositoryPort() {
        AtomicReference<Feedback> saved = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        FeedbackRepository repository = new FeedbackRepository() {
            @Override public FeedbackId save(Feedback feedback, String idempotencyKey) {
                saved.set(feedback);
                key.set(idempotencyKey);
                return feedback.id();
            }
            @Override public Optional<Feedback> findById(TenantId tenantId, FeedbackId id) {
                return Optional.empty();
            }
        };
        TenantId tenantId = TenantId.generate();
        SubmitFeedbackHandler handler = new SubmitFeedbackHandler(repository);
        FeedbackId id = handler.handle(new SubmitFeedbackCommand(tenantId,
                new FeedbackActor("user", UUID.randomUUID().toString()),
                FeedbackKind.COMMENT, null, "General feedback", "Useful product.",
                FeedbackContext.empty(), " request-1 ", Instant.parse("2026-11-10T12:00:00Z")));

        assertEquals(id, saved.get().id());
        assertEquals(tenantId, saved.get().tenantId());
        assertEquals(FeedbackPriority.NORMAL, saved.get().priority());
        assertEquals(FeedbackKind.COMMENT, saved.get().kind());
        assertEquals("request-1", key.get());
    }

    @Test
    void rejectsMissingTenantAndIdempotencyKeyBeforePersistence() {
        FeedbackRepository repository = new FeedbackRepository() {
            @Override public FeedbackId save(Feedback feedback, String key) {
                fail("must not persist");
                return feedback.id();
            }
            @Override public Optional<Feedback> findById(TenantId tenantId, FeedbackId id) {
                return Optional.empty();
            }
        };
        SubmitFeedbackHandler handler = new SubmitFeedbackHandler(repository);
        SubmitFeedbackCommand missingTenant = new SubmitFeedbackCommand(null,
                new FeedbackActor("user", "u-1"), FeedbackKind.BUG, FeedbackPriority.HIGH,
                "Bug", "Description", FeedbackContext.empty(), "key",
                Instant.parse("2026-11-10T12:00:00Z"));
        assertThrows(NullPointerException.class, () -> handler.handle(missingTenant));

        SubmitFeedbackCommand missingKey = new SubmitFeedbackCommand(TenantId.generate(),
                new FeedbackActor("user", "u-1"), FeedbackKind.BUG, FeedbackPriority.HIGH,
                "Bug", "Description", FeedbackContext.empty(), " ",
                Instant.parse("2026-11-10T12:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> handler.handle(missingKey));
    }
}
