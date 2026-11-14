package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPublicInvitationRateLimitRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() throws Exception {
        DataSource ds = dataSource();
        for (String migration : List.of(
                "V1__feedback_foundation.sql",
                "V2__feedback_attachments.sql",
                "V3__feedback_ratings_reviews.sql",
                "V4__feedback_outbox_leases_and_dead_letters.sql",
                "V5__feedback_survey_catalog_distribution.sql",
                "V6__feedback_public_invitation_rate_limits.sql")) {
            String sql = Files.readString(Path.of("src/main/resources/db/migration", migration));
            try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement()) {
                for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
            }
        }
    }

    @Test
    void fixedWindowEnforcesLimitAndAllowsNextWindow() {
        var repository = new PostgresPublicInvitationRateLimitRepository(dataSource());
        String tokenHash = "a".repeat(64);
        Instant window = Instant.parse("2026-11-14T06:00:00Z");

        assertTrue(repository.tryConsume(tokenHash, window, 2));
        assertTrue(repository.tryConsume(tokenHash, window, 2));
        assertFalse(repository.tryConsume(tokenHash, window, 2));
        assertTrue(repository.tryConsume(tokenHash, window.plusSeconds(60), 2));
    }

    @Test
    void concurrentRequestsCannotOvershootWindowLimit() throws Exception {
        var repository = new PostgresPublicInvitationRateLimitRepository(dataSource());
        String tokenHash = "b".repeat(64);
        Instant window = Instant.parse("2026-11-14T06:01:00Z");
        int workers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return repository.tryConsume(tokenHash, window, 3);
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        long admitted = 0;
        for (var future : futures) if (future.get(10, TimeUnit.SECONDS)) admitted++;
        pool.shutdownNow();
        assertEquals(3, admitted);
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }
}