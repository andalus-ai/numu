package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.feedback.domain.Feedback;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackKind;
import tech.kayys.numou.feedback.domain.FeedbackPriority;
import tech.kayys.numou.feedback.domain.comment.FeedbackComment;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentVisibility;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresFeedbackCommentRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void setUpSchema() throws Exception {
        DataSource dataSource = dataSource();
        executeMigration(dataSource, "V1__feedback_foundation.sql");
        executeMigration(dataSource, "V3__feedback_ratings_reviews.sql");
        executeMigration(dataSource, "V7__feedback_discussion_comments.sql");
    }

    @Test
    void commentsAreIdempotentTenantScopedAndVisibilityFiltered() throws Exception {
        DataSource dataSource = dataSource();
        PostgresFeedbackRepository feedbacks = new PostgresFeedbackRepository(dataSource);
        PostgresFeedbackCommentRepository comments = new PostgresFeedbackCommentRepository(dataSource);
        TenantId tenant = TenantId.generate();
        Feedback feedback = Feedback.submit(FeedbackId.generate(), tenant,
                new FeedbackActor("user", "u-1"), FeedbackKind.BUG, FeedbackPriority.NORMAL,
                "Checkout fails", "Checkout returns an error.", null, Instant.parse("2026-11-17T00:00:00Z"));
        feedbacks.save(feedback, "feedback-001");

        FeedbackComment publicComment = FeedbackComment.add(FeedbackCommentId.generate(), tenant,
                feedback.id(), null, new FeedbackActor("user", "u-1"),
                FeedbackCommentVisibility.PUBLIC, "I can reproduce this.", Instant.parse("2026-11-17T00:01:00Z"));
        FeedbackCommentId saved = comments.save(publicComment, "comment-001");
        FeedbackCommentId retry = comments.save(FeedbackComment.add(FeedbackCommentId.generate(), tenant,
                feedback.id(), null, new FeedbackActor("user", "u-1"),
                FeedbackCommentVisibility.PUBLIC, "A different retry body.", Instant.parse("2026-11-17T00:02:00Z")),
                "comment-001");

        assertEquals(saved, retry);
        assertEquals(1, comments.findByFeedback(tenant, feedback.id(),
                FeedbackCommentVisibility.PUBLIC, 20, 0).size());
        assertTrue(comments.findByFeedback(tenant, feedback.id(),
                FeedbackCommentVisibility.INTERNAL, 20, 0).isEmpty());
        assertTrue(comments.findById(TenantId.generate(), saved).isEmpty());

        FeedbackComment internal = FeedbackComment.add(FeedbackCommentId.generate(), tenant,
                feedback.id(), null, new FeedbackActor("staff", "s-1"),
                FeedbackCommentVisibility.INTERNAL, "Investigate payment gateway logs.", Instant.parse("2026-11-17T00:03:00Z"));
        comments.save(internal, "comment-002");
        assertEquals(1, comments.findByFeedback(tenant, feedback.id(),
                FeedbackCommentVisibility.INTERNAL, 20, 0).size());

        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             var rows = s.executeQuery("SELECT count(*) FROM feedback_engagement_outbox WHERE event_type = 'feedback.comment-added.v1'")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getLong(1));
        }
    }

    @Test
    void databaseRejectsCrossFeedbackParentReference() throws Exception {
        DataSource dataSource = dataSource();
        PostgresFeedbackRepository feedbacks = new PostgresFeedbackRepository(dataSource);
        PostgresFeedbackCommentRepository comments = new PostgresFeedbackCommentRepository(dataSource);
        TenantId tenant = TenantId.generate();
        Feedback first = newFeedback(feedbacks, tenant, "one");
        Feedback second = newFeedback(feedbacks, tenant, "two");
        FeedbackComment parent = FeedbackComment.add(FeedbackCommentId.generate(), tenant,
                first.id(), null, new FeedbackActor("user", "u-2"),
                FeedbackCommentVisibility.PUBLIC, "Parent.", Instant.parse("2026-11-17T01:00:00Z"));
        comments.save(parent, "parent");
        FeedbackComment invalidChild = FeedbackComment.add(FeedbackCommentId.generate(), tenant,
                second.id(), parent.id(), new FeedbackActor("user", "u-2"),
                FeedbackCommentVisibility.PUBLIC, "Cross-feedback reply.", Instant.parse("2026-11-17T01:01:00Z"));
        assertThrows(FeedbackPersistenceException.class, () -> comments.save(invalidChild, "child"));
    }

    private static Feedback newFeedback(PostgresFeedbackRepository repository, TenantId tenant, String key) {
        Feedback feedback = Feedback.submit(FeedbackId.generate(), tenant, new FeedbackActor("user", "u-2"),
                FeedbackKind.IDEA, FeedbackPriority.NORMAL, "Feedback " + key, "Description " + key,
                null, Instant.parse("2026-11-17T01:00:00Z"));
        repository.save(feedback, "feedback-" + key);
        return feedback;
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private static void executeMigration(DataSource dataSource, String file) throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration", file));
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) s.execute(statement);
            }
        }
    }
}