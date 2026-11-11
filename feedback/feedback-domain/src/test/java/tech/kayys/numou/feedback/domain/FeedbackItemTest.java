package tech.kayys.numou.feedback.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackItemTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void submissionIsTenantScopedAndRaisesEvent() {
        UUID tenant = UUID.randomUUID();
        FeedbackItem item = FeedbackItem.submit(FeedbackId.generate(), tenant, null,
                FeedbackType.BUG, "  Checkout fails  ", "The checkout page returns an error.",
                "pos", "checkout", "order:123", FeedbackPriority.HIGH, NOW);

        assertEquals(tenant, item.tenantId());
        assertNull(item.submitterId());
        assertEquals("Checkout fails", item.title());
        assertEquals(FeedbackStatus.SUBMITTED, item.status());
        assertEquals(1, item.pendingEvents().size());
        assertInstanceOf(FeedbackSubmitted.class, item.pendingEvents().getFirst());
    }

    @Test
    void statusTransitionsAreValidatedAndVersioned() {
        FeedbackItem item = create();
        item.changeStatus(FeedbackStatus.TRIAGED, NOW.plusSeconds(1));
        item.changeStatus(FeedbackStatus.ACCEPTED, NOW.plusSeconds(2));
        item.changeStatus(FeedbackStatus.IN_PROGRESS, NOW.plusSeconds(3));

        assertEquals(FeedbackStatus.IN_PROGRESS, item.status());
        assertEquals(3, item.version());
        assertEquals(4, item.pendingEvents().size());
    }

    @Test
    void invalidTransitionIsRejectedWithoutMutation() {
        FeedbackItem item = create();

        assertThrows(IllegalStateException.class,
                () -> item.changeStatus(FeedbackStatus.RESOLVED, NOW.plusSeconds(1)));
        assertEquals(FeedbackStatus.SUBMITTED, item.status());
        assertEquals(0, item.version());
    }

    @Test
    void closedFeedbackCannotBeChanged() {
        FeedbackItem item = create();
        item.changeStatus(FeedbackStatus.TRIAGED, NOW.plusSeconds(1));
        item.changeStatus(FeedbackStatus.REJECTED, NOW.plusSeconds(2));
        item.changeStatus(FeedbackStatus.CLOSED, NOW.plusSeconds(3));

        assertThrows(IllegalStateException.class,
                () -> item.changePriority(FeedbackPriority.CRITICAL, NOW.plusSeconds(4)));
    }

    @Test
    void requiredTextIsValidatedAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> FeedbackItem.submit(
                FeedbackId.generate(), UUID.randomUUID(), null, FeedbackType.IDEA,
                " ", "Description", null, null, null, FeedbackPriority.NORMAL, NOW));
        assertThrows(IllegalArgumentException.class, () -> FeedbackItem.submit(
                FeedbackId.generate(), UUID.randomUUID(), null, FeedbackType.IDEA,
                "Title", "x".repeat(20_001), null, null, null, FeedbackPriority.NORMAL, NOW));
    }

    private FeedbackItem create() {
        return FeedbackItem.submit(FeedbackId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                FeedbackType.IDEA, "Add export", "Please add CSV export.", "erp",
                "reporting", null, FeedbackPriority.NORMAL, NOW);
    }
}