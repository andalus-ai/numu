package tech.kayys.numou.content.domain.editorial;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EditorialReviewTest {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final TenantId TENANT = TenantId.of("10000000-0000-0000-0000-000000000001");
    private static final ContentId CONTENT = ContentId.of("20000000-0000-0000-0000-000000000001");
    private static UUID reviewer(int n) { return UUID.fromString("30000000-0000-0000-0000-00000000000"+n); }

    @Test
    void singlePolicyApprovesAfterOneAssignedReviewerApproves() {
        UUID editor = reviewer(1);
        EditorialReview review = EditorialReview.submit(TENANT, CONTENT, 4,
                new EditorialReviewPolicy(EditorialReviewPolicy.Mode.SINGLE, List.of(editor), 1), NOW);
        EditorialReview decided = review.decide(editor, EditorialReview.Decision.APPROVE, null, NOW.plusSeconds(1));
        assertEquals(EditorialReview.Status.APPROVED, decided.status());
        assertEquals(4, decided.revisionNumber());
        assertEquals(1, decided.version());
        assertSame(decided, decided.decide(editor, EditorialReview.Decision.APPROVE, null, NOW.plusSeconds(2)),
                "A retry of the same terminal decision must be idempotent");
    }

    @Test
    void allPolicyWaitsForEveryReviewer() {
        UUID first = reviewer(1), second = reviewer(2);
        EditorialReview review = EditorialReview.submit(TENANT, CONTENT, 1,
                new EditorialReviewPolicy(EditorialReviewPolicy.Mode.ALL, List.of(first, second), 2), NOW);
        EditorialReview one = review.decide(first, EditorialReview.Decision.APPROVE, null, NOW.plusSeconds(1));
        assertEquals(EditorialReview.Status.IN_REVIEW, one.status());
        EditorialReview two = one.decide(second, EditorialReview.Decision.APPROVE, null, NOW.plusSeconds(2));
        assertEquals(EditorialReview.Status.APPROVED, two.status());
    }

    @Test
    void sequentialPolicyRejectsOutOfOrderDecision() {
        UUID first = reviewer(1), second = reviewer(2);
        EditorialReview review = EditorialReview.submit(TENANT, CONTENT, 1,
                new EditorialReviewPolicy(EditorialReviewPolicy.Mode.SEQUENTIAL, List.of(first, second), 2), NOW);
        assertThrows(IllegalStateException.class,
                () -> review.decide(second, EditorialReview.Decision.APPROVE, null, NOW.plusSeconds(1)));
    }

    @Test
    void changeRequestRequiresExplanationAndEndsReview() {
        UUID editor = reviewer(1);
        EditorialReview review = EditorialReview.submit(TENANT, CONTENT, 2,
                new EditorialReviewPolicy(EditorialReviewPolicy.Mode.SINGLE, List.of(editor), 1), NOW);
        assertThrows(IllegalArgumentException.class,
                () -> review.decide(editor, EditorialReview.Decision.REQUEST_CHANGES, "  ", NOW.plusSeconds(1)));
        EditorialReview changed = review.decide(editor, EditorialReview.Decision.REQUEST_CHANGES,
                "Please correct the source citation", NOW.plusSeconds(1));
        assertEquals(EditorialReview.Status.CHANGES_REQUESTED, changed.status());
        assertThrows(IllegalStateException.class,
                () -> changed.comment(new EditorialReview.ReviewComment(UUID.randomUUID(), editor,
                        "Late comment", NOW.plusSeconds(2)), NOW.plusSeconds(2)));
    }

    @Test
    void commentsAreIdempotentByCommentId() {
        UUID editor = reviewer(1), commentId = UUID.randomUUID();
        EditorialReview review = EditorialReview.submit(TENANT, CONTENT, 3,
                new EditorialReviewPolicy(EditorialReviewPolicy.Mode.SINGLE, List.of(editor), 1), NOW);
        var comment = new EditorialReview.ReviewComment(commentId, editor, "Please review the image caption", NOW);
        EditorialReview added = review.comment(comment, NOW.plusSeconds(1));
        EditorialReview approved = added.decide(editor, EditorialReview.Decision.APPROVE, null, NOW.plusSeconds(2));
        EditorialReview replay = approved.comment(comment, NOW.plusSeconds(3));
        assertEquals(1, replay.comments().size());
        assertEquals(2, replay.version());
        assertSame(approved, replay, "A retry of a comment after terminal approval must be idempotent");
        assertThrows(IllegalStateException.class, () -> added.comment(
                new EditorialReview.ReviewComment(commentId, editor, "Different text", NOW.plusSeconds(3)),
                NOW.plusSeconds(3)));
    }

    @Test
    void policyRejectsDuplicateReviewerAndInvalidThreshold() {
        UUID editor = reviewer(1);
        assertThrows(IllegalArgumentException.class, () ->
                new EditorialReviewPolicy(EditorialReviewPolicy.Mode.ALL, List.of(editor, editor), 2));
        assertThrows(IllegalArgumentException.class, () ->
                new EditorialReviewPolicy(EditorialReviewPolicy.Mode.SINGLE, List.of(editor), 2));
    }
}
