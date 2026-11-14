package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.domain.survey.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSurveyDistributionQuotaTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final Instant NOW = Instant.parse("2099-01-01T12:00:00Z");

    @BeforeAll
    static void setUpSchema() throws Exception {
        DataSource ds = dataSource();
        for (String migration : List.of(
                "V1__feedback_foundation.sql",
                "V2__feedback_attachments.sql",
                "V3__feedback_ratings_reviews.sql",
                "V4__feedback_outbox_leases_and_dead_letters.sql",
                "V5__feedback_survey_catalog_distribution.sql")) {
            String sql = Files.readString(Path.of("src/main/resources/db/migration", migration));
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                for (String statement : sql.split(";")) if (!statement.isBlank()) s.execute(statement);
            }
        }
    }

    @Test
    void concurrentReservationsCannotExceedDistributionQuota() throws Exception {
        var repository = new PostgresFeedbackSurveyRepository(dataSource());
        TenantId tenant = TenantId.generate();
        SurveyDistribution distribution = new SurveyDistribution(UUID.randomUUID(), tenant, "survey-atomic",
                DistributionChannel.PUBLIC_LINK, "a".repeat(64), NOW.plusSeconds(3600), 1,
                DistributionStatus.ACTIVE, NOW.minusSeconds(1));
        repository.save(distribution);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SurveyDistributionRepository.ReservationResult>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final String responseId = "response-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return repository.reserveResponse(tenant, distribution.id(), responseId, NOW);
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        List<SurveyDistributionRepository.ReservationResult> results = new ArrayList<>();
        for (Future<SurveyDistributionRepository.ReservationResult> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, results.stream().filter(r -> r == SurveyDistributionRepository.ReservationResult.RESERVED).count());
        assertEquals(1, results.stream().filter(r -> r == SurveyDistributionRepository.ReservationResult.LIMIT_REACHED).count());
        String reservedResponse = results.get(0) == SurveyDistributionRepository.ReservationResult.RESERVED
                ? "response-0" : "response-1";
        repository.completeResponse(tenant, distribution.id(), reservedResponse, NOW.plusSeconds(1));

        try (Connection c = dataSource().getConnection();
             var s = c.prepareStatement("SELECT accepted_responses, reserved_responses FROM feedback_survey_distributions WHERE tenant_id = ? AND id = ?")) {
            s.setObject(1, tenant.value());
            s.setObject(2, distribution.id());
            try (var rows = s.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(0, rows.getInt(2));
            }
        }
        assertTrue(repository.findById(TenantId.generate(), distribution.id()).isEmpty());
    }

    @Test
    void revokedDistributionCannotReserveFurtherResponses() {
        var repository = new PostgresFeedbackSurveyRepository(dataSource());
        TenantId tenant = TenantId.generate();
        SurveyDistribution distribution = new SurveyDistribution(UUID.randomUUID(), tenant, "survey-revoke",
                DistributionChannel.QR_CODE, "b".repeat(64), NOW.plusSeconds(3600), 5,
                DistributionStatus.ACTIVE, NOW.minusSeconds(1));
        repository.save(distribution);

        assertTrue(repository.revoke(tenant, distribution.id(), NOW));
        assertFalse(repository.revoke(tenant, distribution.id(), NOW.plusSeconds(1)));
        assertEquals(SurveyDistributionRepository.ReservationResult.NOT_ACTIVE,
                repository.reserveResponse(tenant, distribution.id(), "response-revoked", NOW.plusSeconds(2)));
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }
}
