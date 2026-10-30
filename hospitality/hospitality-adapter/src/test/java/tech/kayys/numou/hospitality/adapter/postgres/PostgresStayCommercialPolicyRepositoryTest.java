package tech.kayys.numou.hospitality.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.hospitality.domain.NightlyRateOverride;
import tech.kayys.numou.hospitality.domain.StayDateRestriction;
import tech.kayys.numou.hospitality.domain.StayRatePlan;

@Testcontainers(disabledWithoutDocker = true)
class PostgresStayCommercialPolicyRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("numou_hospitality_policy")
            .withUsername("numou")
            .withPassword("numou");

    private ExecutorService jdbcExecutor;
    private DataSource dataSource;
    private PostgresStayCommercialPolicyRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        postgresDataSource.setURL(POSTGRES.getJdbcUrl());
        postgresDataSource.setUser(POSTGRES.getUsername());
        postgresDataSource.setPassword(POSTGRES.getPassword());
        dataSource = postgresDataSource;
        jdbcExecutor = Executors.newFixedThreadPool(4);
        repository = new PostgresStayCommercialPolicyRepository(dataSource, jdbcExecutor);
        applyMigration("/db/migration/V1__hospitality_inventory_holds.sql");
        applyMigration("/db/migration/V2__index_due_hospitality_booking_holds.sql");
        applyMigration("/db/migration/V3__hospitality_commercial_policies.sql");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE hospitality_nightly_rate_override, hospitality_stay_date_restriction, hospitality_stay_rate_plan CASCADE");
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        jdbcExecutor.shutdownNow();
        assertThat(jdbcExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void persistsRatePlansAndKeepsIdentifiersTenantScoped() {
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        StayRatePlan plan = plan(UUID.randomUUID(), listing, unit, "IDR", "BAR");

        repository.saveRatePlan("tenant-a", plan).await().indefinitely();

        assertThat(repository.findRatePlan("tenant-a", listing, unit, plan.id()).await().indefinitely())
                .get().satisfies(actual -> assertRatePlanEqualsIgnoringScale(actual, plan));
        assertThat(repository.findRatePlan("tenant-b", listing, unit, plan.id()).await().indefinitely()).isEmpty();
        List<StayRatePlan> plans = repository.findRatePlans("tenant-a", listing, unit).await().indefinitely();
        assertThat(plans).hasSize(1);
        assertRatePlanEqualsIgnoringScale(plans.get(0), plan);
    }

    @Test
    void upsertsNightlyOverridesAndReadsExclusiveDateRange() {
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        repository.saveRatePlan("tenant-a", plan(planId, listing, unit, "IDR", "BAR")).await().indefinitely();
        LocalDate first = LocalDate.of(2031, 1, 10);
        NightlyRateOverride overrideA = new NightlyRateOverride(planId, first, new BigDecimal("550000"));
        NightlyRateOverride overrideB = new NightlyRateOverride(planId, first.plusDays(1), new BigDecimal("600000"));
        repository.saveNightlyRateOverrides("tenant-a", listing, unit, planId, List.of(overrideA, overrideB))
                .await().indefinitely();
        NightlyRateOverride revised = new NightlyRateOverride(planId, first, new BigDecimal("575000"));
        repository.saveNightlyRateOverrides("tenant-a", listing, unit, planId, List.of(revised))
                .await().indefinitely();

        List<NightlyRateOverride> persisted = repository.findNightlyRateOverrides("tenant-a", listing, unit, planId,
                first, first.plusDays(1)).await().indefinitely();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).ratePlanId()).isEqualTo(revised.ratePlanId());
        assertThat(persisted.get(0).date()).isEqualTo(revised.date());
        assertThat(persisted.get(0).amount()).isEqualByComparingTo(revised.amount());
        assertThat(repository.findNightlyRateOverrides("tenant-b", listing, unit, planId,
                first, first.plusDays(2)).await().indefinitely()).isEmpty();
    }

    @Test
    void upsertsDateRestrictionsWithoutCrossTenantLeakage() {
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        LocalDate date = LocalDate.of(2031, 2, 12);
        StayDateRestriction restriction = new StayDateRestriction(listing, unit, date,
                2, 5, true, false, true);
        repository.saveStayDateRestrictions("tenant-a", listing, unit, List.of(restriction))
                .await().indefinitely();

        StayDateRestriction revised = new StayDateRestriction(listing, unit, date,
                3, 6, false, true, false);
        repository.saveStayDateRestrictions("tenant-a", listing, unit, List.of(revised))
                .await().indefinitely();

        assertThat(repository.findStayDateRestrictions("tenant-a", listing, unit,
                date, date.plusDays(1)).await().indefinitely()).containsExactly(revised);
        assertThat(repository.findStayDateRestrictions("tenant-b", listing, unit,
                date, date.plusDays(1)).await().indefinitely()).isEmpty();
    }

    @Test
    void rollsBackAllOverridesWhenOneRowInTheBatchFails() throws Exception {
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        LocalDate first = LocalDate.of(2031, 3, 1);
        repository.saveRatePlan("tenant-a", plan(planId, listing, unit, "IDR", "BAR"))
                .await().indefinitely();
        NightlyRateOverride valid = new NightlyRateOverride(planId, first, new BigDecimal("500000"));
        NightlyRateOverride failing = new NightlyRateOverride(planId, first.plusDays(1), new BigDecimal("600000"));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE OR REPLACE FUNCTION hospitality_test_fail_rate_override()
                    RETURNS trigger
                    LANGUAGE plpgsql
                    AS 'BEGIN
                        IF NEW.stay_date = DATE ''2031-03-02'' THEN
                            RAISE EXCEPTION ''injected rate override failure'';
                        END IF;
                        RETURN NEW;
                    END;'
                    """);
            statement.execute("""
                    CREATE TRIGGER hospitality_test_fail_rate_override
                    BEFORE INSERT ON hospitality_nightly_rate_override
                    FOR EACH ROW EXECUTE FUNCTION hospitality_test_fail_rate_override()
                    """);
        }

        try {
            assertThatThrownBy(() -> repository.saveNightlyRateOverrides("tenant-a", listing, unit,
                    planId, List.of(valid, failing)).await().indefinitely())
                    .isInstanceOf(RuntimeException.class);
            assertThat(repository.findNightlyRateOverrides("tenant-a", listing, unit, planId,
                    first, first.plusDays(2)).await().indefinitely()).isEmpty();
        } finally {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS hospitality_test_fail_rate_override ON hospitality_nightly_rate_override");
                statement.execute("DROP FUNCTION IF EXISTS hospitality_test_fail_rate_override()");
            }
        }
    }

    @Test
    void rejectsRatePlanIdentifierReusedAcrossDifferentUnitScope() {
        UUID listing = UUID.randomUUID();
        UUID unitA = UUID.randomUUID();
        UUID unitB = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        repository.saveRatePlan("tenant-a", plan(planId, listing, unitA, "IDR", "BAR"))
                .await().indefinitely();

        StayRatePlan conflicting = plan(planId, listing, unitB, "IDR", "BAR");
        assertThatThrownBy(() -> repository.saveRatePlan("tenant-a", conflicting).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRatePlanEqualsIgnoringScale(StayRatePlan actual, StayRatePlan expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.listingId()).isEqualTo(expected.listingId());
        assertThat(actual.unitId()).isEqualTo(expected.unitId());
        assertThat(actual.code()).isEqualTo(expected.code());
        assertThat(actual.name()).isEqualTo(expected.name());
        assertThat(actual.currency()).isEqualTo(expected.currency());
        assertThat(actual.baseNightlyRate()).isEqualByComparingTo(expected.baseNightlyRate());
        assertThat(actual.validFrom()).isEqualTo(expected.validFrom());
        assertThat(actual.validUntil()).isEqualTo(expected.validUntil());
        assertThat(actual.enabled()).isEqualTo(expected.enabled());
    }

    private static StayRatePlan plan(UUID id, UUID listing, UUID unit, String currency, String code) {
        return new StayRatePlan(id, listing, unit, code, "Best available", currency,
                new BigDecimal("500000"), LocalDate.of(2031, 1, 1),
                LocalDate.of(2032, 1, 1), true);
    }

    private void applyMigration(String resource) throws Exception {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing migration resource " + resource);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
