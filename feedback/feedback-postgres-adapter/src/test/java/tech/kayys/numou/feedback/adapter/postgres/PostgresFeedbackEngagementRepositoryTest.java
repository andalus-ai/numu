package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.*;
import tech.kayys.numou.feedback.domain.review.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresFeedbackEngagementRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void setUpSchema() throws Exception {
        DataSource dataSource = dataSource();
        executeMigration(dataSource, "V1__feedback_foundation.sql");
        executeMigration(dataSource, "V2__feedback_ratings_reviews.sql");
    }

    @Test
    void ratingPersistenceIsIdempotentAndTenantScoped() {
        DataSource dataSource = dataSource();
        PostgresFeedbackRatingRepository repository = new PostgresFeedbackRatingRepository(dataSource);
        TenantId tenant = TenantId.generate();
        RatingTarget target = new RatingTarget("product", "sku-001", "branch-01");
        FeedbackRating rating = FeedbackRating.submit(FeedbackRatingId.generate(), tenant, target,
                RatingMetric.NPS, RatingScale.of(0, 10), BigDecimal.TEN,
                new FeedbackActor("user", "user-1"), "survey-01", Instant.parse("2026-11-11T10:00:00Z"));

        FeedbackRatingId first = repository.save(rating, "nps-response-01");
        FeedbackRatingId retry = repository.save(FeedbackRating.submit(FeedbackRatingId.generate(),
                tenant, target, RatingMetric.NPS, RatingScale.of(0, 10), BigDecimal.ONE,
                null, null, Instant.parse("2026-11-11T10:01:00Z")), "nps-response-01");

        assertEquals(first, retry);
        assertEquals(1, repository.findByTarget(tenant, target, RatingMetric.NPS).size());
        assertEquals(0, BigDecimal.TEN.compareTo(repository.findById(tenant, first).orElseThrow().value()));
        assertTrue(repository.findById(TenantId.generate(), first).isEmpty());
    }

    @Test
    void reviewModerationStateRoundTripsAndUpdatesWithinTenant() {
        DataSource dataSource = dataSource();
        PostgresFeedbackRatingRepository ratings = new PostgresFeedbackRatingRepository(dataSource);
        PostgresFeedbackReviewRepository reviews = new PostgresFeedbackReviewRepository(dataSource);
        TenantId tenant = TenantId.generate();
        RatingTarget target = new RatingTarget("service", "delivery-9", "branch-2");
        Instant now = Instant.parse("2026-11-11T10:00:00Z");
        FeedbackRating rating = FeedbackRating.submit(FeedbackRatingId.generate(), tenant, target,
                RatingMetric.STAR_RATING, RatingScale.of(1, 5), BigDecimal.valueOf(5),
                new FeedbackActor("user", "user-9"), null, now);
        ratings.save(rating, "review-rating-9");

        FeedbackReview review = FeedbackReview.submit(FeedbackReviewId.generate(), tenant, target,
                new FeedbackActor("user", "user-9"), rating.id(), "Excellent service",
                "The delivery arrived earlier than expected.", now);
        reviews.save(review);
        review.verify(now.plusSeconds(1));
        review.publish(now.plusSeconds(2));
        reviews.update(review);

        FeedbackReview loaded = reviews.findById(tenant, review.id()).orElseThrow();
        assertEquals(ReviewStatus.PUBLISHED, loaded.status());
        assertEquals(VerificationStatus.VERIFIED, loaded.verificationStatus());
        assertEquals(now.plusSeconds(2), loaded.moderatedAt());
        assertTrue(reviews.findById(TenantId.generate(), review.id()).isEmpty());
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