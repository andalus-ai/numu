package tech.kayys.numou.feedback.application.command;

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

class AddFeedbackCommentHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-17T02:00:00Z");

    @Test
    void addsRootAndReplyWithinSameFeedbackAndVisibility() {
        TenantId tenant = TenantId.generate();
        Feedback feedback = feedback(tenant);
        InMemoryComments comments = new InMemoryComments();
        AddFeedbackCommentHandler handler = new AddFeedbackCommentHandler(
                feedbackRepository(tenant, feedback), comments, allowAllPolicy());

        FeedbackCommentId rootId = handler.handle(command(tenant, feedback.id(), null,
                FeedbackCommentVisibility.PUBLIC, "root", "root-key"));
        FeedbackCommentId replyId = handler.handle(command(tenant, feedback.id(), rootId,
                FeedbackCommentVisibility.PUBLIC, "reply", "reply-key"));

        assertEquals(rootId, comments.rows.get(rootId).id());
        assertEquals(rootId, comments.rows.get(replyId).parentCommentId());
    }

    @Test
    void rejectsUnknownFeedbackAndCrossFeedbackParent() {
        TenantId tenant = TenantId.generate();
        Feedback feedback = feedback(tenant);
        Feedback other = feedback(tenant);
        InMemoryComments comments = new InMemoryComments();
        FeedbackComment parent = FeedbackComment.add(FeedbackCommentId.generate(), tenant, other.id(), null,
                new FeedbackActor("user", "u-1"), FeedbackCommentVisibility.PUBLIC, "parent", NOW);
        comments.rows.put(parent.id(), parent);
        AddFeedbackCommentHandler handler = new AddFeedbackCommentHandler(
                feedbackRepository(tenant, feedback), comments, allowAllPolicy());

        assertThrows(IllegalArgumentException.class, () -> handler.handle(command(tenant,
                FeedbackId.generate(), null, FeedbackCommentVisibility.PUBLIC, "body", "unknown")));
        assertThrows(IllegalArgumentException.class, () -> handler.handle(command(tenant,
                feedback.id(), parent.id(), FeedbackCommentVisibility.PUBLIC, "body", "cross-feedback")));
    }

    @Test
    void rejectsPublicReplyToInternalNote() {
        TenantId tenant = TenantId.generate();
        Feedback feedback = feedback(tenant);
        InMemoryComments comments = new InMemoryComments();
        FeedbackComment internal = FeedbackComment.add(FeedbackCommentId.generate(), tenant, feedback.id(), null,
                new FeedbackActor("staff", "s-1"), FeedbackCommentVisibility.INTERNAL, "private", NOW);
        comments.rows.put(internal.id(), internal);
        AddFeedbackCommentHandler handler = new AddFeedbackCommentHandler(
                feedbackRepository(tenant, feedback), comments, allowAllPolicy());

        assertThrows(IllegalArgumentException.class, () -> handler.handle(command(tenant, feedback.id(),
                internal.id(), FeedbackCommentVisibility.PUBLIC, "leak attempt", "leak")));
    }

    @Test
    void rejectsUnauthorizedInternalNoteAndReplyThatPredatesParent() {
        TenantId tenant = TenantId.generate();
        Feedback feedback = feedback(tenant);
        InMemoryComments comments = new InMemoryComments();
        FeedbackComment parent = FeedbackComment.add(FeedbackCommentId.generate(), tenant, feedback.id(), null,
                new FeedbackActor("user", "u-1"), FeedbackCommentVisibility.PUBLIC, "parent", NOW);
        comments.rows.put(parent.id(), parent);
        FeedbackCommentAccessPolicy denyInternal = new FeedbackCommentAccessPolicy() {
            @Override public void assertCanAdd(TenantId t, FeedbackId id,
                    FeedbackActor actor, FeedbackCommentVisibility visibility) {
                if (visibility == FeedbackCommentVisibility.INTERNAL) throw new SecurityException("forbidden");
            }
            @Override public void assertCanRead(TenantId t, FeedbackId id,
                    FeedbackActor actor, FeedbackCommentVisibility visibility) {}
        };
        AddFeedbackCommentHandler secured = new AddFeedbackCommentHandler(
                feedbackRepository(tenant, feedback), comments, denyInternal);
        assertThrows(SecurityException.class, () -> secured.handle(command(tenant, feedback.id(), null,
                FeedbackCommentVisibility.INTERNAL, "private", "denied")));

        AddFeedbackCommentHandler allowed = new AddFeedbackCommentHandler(
                feedbackRepository(tenant, feedback), comments, allowAllPolicy());
        AddFeedbackCommentCommand earlierReply = new AddFeedbackCommentCommand(tenant, feedback.id(), parent.id(),
                new FeedbackActor("user", "u-1"), FeedbackCommentVisibility.PUBLIC,
                "too early", "early", NOW.minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> allowed.handle(earlierReply));
    }

    private static FeedbackCommentAccessPolicy allowAllPolicy() {
        return new FeedbackCommentAccessPolicy() {
            @Override public void assertCanAdd(TenantId tenant, FeedbackId feedback,
                    FeedbackActor actor, FeedbackCommentVisibility visibility) {}
            @Override public void assertCanRead(TenantId tenant, FeedbackId feedback,
                    FeedbackActor actor, FeedbackCommentVisibility visibility) {}
        };
    }

    private static FeedbackRepository feedbackRepository(TenantId tenant, Feedback feedback) {
        return new FeedbackRepository() {
            @Override public FeedbackId save(Feedback aggregate, String idempotencyKey) {
                throw new UnsupportedOperationException("not used by this test");
            }
            @Override public Optional<Feedback> findById(TenantId requestedTenant, FeedbackId id) {
                return requestedTenant.equals(tenant) && id.equals(feedback.id())
                        ? Optional.of(feedback) : Optional.empty();
            }
        };
    }

    private static AddFeedbackCommentCommand command(TenantId tenant, FeedbackId feedback,
            FeedbackCommentId parent, FeedbackCommentVisibility visibility, String body, String key) {
        return new AddFeedbackCommentCommand(tenant, feedback, parent,
                new FeedbackActor("user", "u-1"), visibility, body, key, NOW);
    }

    private static Feedback feedback(TenantId tenant) {
        return Feedback.submit(FeedbackId.generate(), tenant, new FeedbackActor("user", "u-1"),
                FeedbackKind.BUG, FeedbackPriority.NORMAL, "Bug report", "Something failed",
                null, NOW);
    }

    private static final class InMemoryComments implements FeedbackCommentRepository {
        private final Map<FeedbackCommentId, FeedbackComment> rows = new HashMap<>();
        private final Map<String, FeedbackCommentId> idempotency = new HashMap<>();

        @Override public FeedbackCommentId save(FeedbackComment comment, String key) {
            return idempotency.computeIfAbsent(comment.tenantId() + ":" + key, ignored -> {
                rows.put(comment.id(), comment);
                return comment.id();
            });
        }
        @Override public Optional<FeedbackComment> findById(TenantId tenant, FeedbackCommentId id) {
            FeedbackComment comment = rows.get(id);
            return comment != null && comment.tenantId().equals(tenant) ? Optional.of(comment) : Optional.empty();
        }
        @Override public List<FeedbackComment> findByFeedback(TenantId tenant, FeedbackId feedback,
                FeedbackCommentVisibility visibility, int limit, int offset) {
            return rows.values().stream().filter(c -> c.tenantId().equals(tenant)
                    && c.feedbackId().equals(feedback) && c.visibility() == visibility).toList();
        }
    }
}