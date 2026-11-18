package tech.kayys.numou.feedback.domain.comment;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackCommentTest {
    @Test
    void createsPublicRootCommentAndInternalReply() {
        TenantId tenant = TenantId.generate();
        FeedbackId feedback = FeedbackId.generate();
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        FeedbackComment root = FeedbackComment.add(FeedbackCommentId.generate(), tenant, feedback,
                null, new FeedbackActor("user", "u-1"), FeedbackCommentVisibility.PUBLIC,
                "  Please investigate this issue.  ", now);
        FeedbackComment reply = FeedbackComment.add(FeedbackCommentId.generate(), tenant, feedback,
                root.id(), new FeedbackActor("staff", "s-1"), FeedbackCommentVisibility.PUBLIC,
                "We are investigating.", now.plusSeconds(1));

        assertNull(root.parentCommentId());
        assertEquals("Please investigate this issue.", root.body());
        assertEquals(root.id(), reply.parentCommentId());
        assertEquals(FeedbackCommentVisibility.PUBLIC, reply.visibility());
    }

    @Test
    void requiresTenantFeedbackAuthorVisibilityAndBoundedBody() {
        assertThrows(NullPointerException.class, () -> new FeedbackComment(
                FeedbackCommentId.generate(), null, FeedbackId.generate(), null,
                new FeedbackActor("user", "u-1"), FeedbackCommentVisibility.PUBLIC,
                "comment", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> FeedbackComment.add(
                FeedbackCommentId.generate(), TenantId.generate(), FeedbackId.generate(), null,
                new FeedbackActor("user", "u-1"), FeedbackCommentVisibility.INTERNAL, " ", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> FeedbackComment.add(
                FeedbackCommentId.generate(), TenantId.generate(), FeedbackId.generate(), null,
                new FeedbackActor("user", "u-1"), FeedbackCommentVisibility.INTERNAL,
                "x".repeat(20_001), Instant.now()));
    }
}