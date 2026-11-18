package tech.kayys.numou.feedback.application.query;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.FeedbackCommentAccessPolicy;
import tech.kayys.numou.feedback.application.port.FeedbackCommentRepository;
import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.*;
import tech.kayys.numou.feedback.domain.comment.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ListFeedbackCommentsHandlerTest {
    @Test
    void requestsOnlyExplicitVisibilityAndTenant() {
        TenantId tenant = TenantId.generate();
        Feedback feedback = Feedback.submit(FeedbackId.generate(), tenant,
                new FeedbackActor("user", "u-1"), FeedbackKind.BUG, FeedbackPriority.NORMAL,
                "Bug", "Description", null, Instant.parse("2026-11-17T03:00:00Z"));
        FeedbackActor requester = new FeedbackActor("user", "u-1");
        FeedbackComment publicComment = FeedbackComment.add(FeedbackCommentId.generate(), tenant,
                feedback.id(), null, requester, FeedbackCommentVisibility.PUBLIC,
                "Public update", Instant.parse("2026-11-17T03:01:00Z"));
        final FeedbackCommentVisibility[] requested = new FeedbackCommentVisibility[1];
        FeedbackRepository feedbackRepository = new FeedbackRepository() {
            @Override public FeedbackId save(Feedback value, String key) { throw new UnsupportedOperationException(); }
            @Override public Optional<Feedback> findById(TenantId requestedTenant, FeedbackId id) {
                return requestedTenant.equals(tenant) && id.equals(feedback.id())
                        ? Optional.of(feedback) : Optional.empty();
            }
        };
        FeedbackCommentRepository commentRepository = new FeedbackCommentRepository() {
            @Override public FeedbackCommentId save(FeedbackComment value, String key) { return value.id(); }
            @Override public Optional<FeedbackComment> findById(TenantId t, FeedbackCommentId id) {
                return Optional.empty();
            }
            @Override public List<FeedbackComment> findByFeedback(TenantId t, FeedbackId id,
                    FeedbackCommentVisibility visibility, int limit, int offset) {
                assertEquals(tenant, t);
                assertEquals(feedback.id(), id);
                assertEquals(20, limit);
                assertEquals(0, offset);
                requested[0] = visibility;
                return visibility == FeedbackCommentVisibility.PUBLIC ? List.of(publicComment) : List.of();
            }
        };
        FeedbackCommentAccessPolicy policy = new FeedbackCommentAccessPolicy() {
            @Override public void assertCanAdd(TenantId t, FeedbackId id,
                    FeedbackActor actor, FeedbackCommentVisibility visibility) {}
            @Override public void assertCanRead(TenantId t, FeedbackId id,
                    FeedbackActor actor, FeedbackCommentVisibility visibility) {
                assertEquals(tenant, t);
                assertEquals(feedback.id(), id);
                assertEquals(requester, actor);
                assertEquals(FeedbackCommentVisibility.PUBLIC, visibility);
            }
        };

        ListFeedbackCommentsHandler handler = new ListFeedbackCommentsHandler(
                feedbackRepository, commentRepository, policy);
        List<FeedbackComment> result = handler.handle(new ListFeedbackCommentsQuery(
                tenant, feedback.id(), requester, FeedbackCommentVisibility.PUBLIC, 20, 0));

        assertEquals(List.of(publicComment), result);
        assertEquals(FeedbackCommentVisibility.PUBLIC, requested[0]);
        assertThrows(IllegalArgumentException.class, () -> handler.handle(
                new ListFeedbackCommentsQuery(tenant, feedback.id(), requester, FeedbackCommentVisibility.PUBLIC, 101, 0)));
        assertThrows(IllegalArgumentException.class, () -> handler.handle(
                new ListFeedbackCommentsQuery(tenant, feedback.id(), requester, FeedbackCommentVisibility.PUBLIC, 20, -1)));
    }
}