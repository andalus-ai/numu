package tech.kayys.numou.feedback.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.feedback.domain.event.FeedbackStatusChanged;
import tech.kayys.numou.feedback.domain.event.FeedbackSubmitted;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackTest {
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Test
    void submissionIsTenantOwnedAndRaisesEvent() {
        Feedback feedback = newFeedback();
        assertNotNull(feedback.tenantId());
        assertEquals(FeedbackStatus.SUBMITTED, feedback.status());
        assertEquals("Invoice total is incorrect", feedback.title());
        assertInstanceOf(FeedbackSubmitted.class, feedback.getDomainEvents().getFirst());
        assertEquals("feedback.submitted.v1", feedback.getDomainEvents().getFirst().eventType());
    }

    @Test
    void kindCodesAreExtensibleButValidated() {
        assertEquals("api-regression", FeedbackKind.of(" API-Regression ").code());
        assertThrows(IllegalArgumentException.class, () -> FeedbackKind.of("x"));
        assertThrows(IllegalArgumentException.class, () -> FeedbackKind.of("invalid kind"));
    }

    @Test
    void lifecycleEnforcesTriageAndResolutionOrder() {
        Feedback feedback = newFeedback();
        assertThrows(InvalidStateException.class, () -> feedback.resolve(NOW));
        feedback.triage(NOW.plusSeconds(1));
        feedback.accept(NOW.plusSeconds(2));
        feedback.startWork(NOW.plusSeconds(3));
        feedback.beginReview(NOW.plusSeconds(4));
        feedback.resolve(NOW.plusSeconds(5));
        assertEquals(FeedbackStatus.RESOLVED, feedback.status());
        assertInstanceOf(FeedbackStatusChanged.class, feedback.getDomainEvents().getLast());
    }

    @Test
    void resolvedFeedbackCanBeReopenedButClosedFeedbackCannotChange() {
        Feedback feedback = newFeedback();
        feedback.triage(NOW.plusSeconds(1));
        feedback.accept(NOW.plusSeconds(2));
        feedback.startWork(NOW.plusSeconds(3));
        feedback.resolve(NOW.plusSeconds(4));
        feedback.reopen(NOW.plusSeconds(5));
        feedback.startWork(NOW.plusSeconds(6));
        feedback.close(NOW.plusSeconds(7));
        assertEquals(FeedbackStatus.CLOSED, feedback.status());
        assertThrows(InvalidStateException.class,
                () -> feedback.changePriority(FeedbackPriority.CRITICAL, NOW.plusSeconds(8)));
    }

    @Test
    void duplicateAndRejectedFeedbackCanOnlyBeClosed() {
        Feedback duplicate = newFeedback();
        duplicate.triage(NOW.plusSeconds(1));
        duplicate.markDuplicate(NOW.plusSeconds(2));
        assertThrows(InvalidStateException.class,
                () -> duplicate.changePriority(FeedbackPriority.HIGH, NOW.plusSeconds(3)));
        duplicate.close(NOW.plusSeconds(4));

        Feedback rejected = newFeedback();
        rejected.triage(NOW.plusSeconds(1));
        rejected.reject(NOW.plusSeconds(2));
        rejected.close(NOW.plusSeconds(3));
        assertEquals(FeedbackStatus.CLOSED, rejected.status());
    }

    @Test
    void contextNormalizesValuesAndRejectsOversizedFields() {
        FeedbackContext context = new FeedbackContext(" numou-erp ", " accounting ",
                " invoice-detail ", "invoice", "inv-123", " 1.4.0 ");
        assertEquals("numou-erp", context.application());
        assertEquals("accounting", context.module());
        assertEquals("1.4.0", context.applicationVersion());
        assertThrows(IllegalArgumentException.class,
                () -> new FeedbackContext("x".repeat(101), null, null, null, null, null));
    }

    @Test
    void submissionRejectsBlankAndOversizedText() {
        assertThrows(IllegalArgumentException.class, () -> Feedback.submit(
                FeedbackId.generate(), TenantId.generate(), new FeedbackActor("user", "u-1"),
                FeedbackKind.BUG, FeedbackPriority.NORMAL, " ", "description", null, NOW));
        assertThrows(IllegalArgumentException.class, () -> Feedback.submit(
                FeedbackId.generate(), TenantId.generate(), new FeedbackActor("user", "u-1"),
                FeedbackKind.BUG, FeedbackPriority.NORMAL, "x".repeat(201), "description", null, NOW));
    }

    private Feedback newFeedback() {
        return Feedback.submit(FeedbackId.generate(), TenantId.generate(),
                new FeedbackActor("user", "user-123"), FeedbackKind.BUG, FeedbackPriority.HIGH,
                "  Invoice total is incorrect  ",
                "The invoice total does not match its line items.",
                new FeedbackContext("numou-erp", "accounting", "invoice-detail",
                        "invoice", "invoice-ref-123", "1.0.0"), NOW);
    }
}