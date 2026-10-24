package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcCourierPlanningRepositoryPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcCourierPlanningRepository planning;
    static JdbcShipmentRepository shipments;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").load().migrate();
        planning = new JdbcCourierPlanningRepository(dataSource());
        shipments = new JdbcShipmentRepository(dataSource());
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl()); ds.setUser(POSTGRES.getUsername()); ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    @Test void etaHistoryIsAppendOnlyAndTenantScoped() {
        TenantId tenant = TenantId.generate();
        TenantId other = TenantId.generate();
        Shipment shipment = shipment(tenant, "ETA-ROUNDTRIP");
        shipments.save(tenant, shipment).toCompletableFuture().join();
        Instant now = Instant.parse("2026-10-24T02:00:00Z");
        CourierEtaEstimate first = new CourierEtaEstimate(UUID.randomUUID(), tenant, shipment.id(),
            now.plusSeconds(3600), now.plusSeconds(7200), now, new BigDecimal("0.70"),
            "rules-v1", CourierEtaEstimate.Source.RULES, "BASELINE");
        CourierEtaEstimate latest = new CourierEtaEstimate(UUID.randomUUID(), tenant, shipment.id(),
            now.plusSeconds(1800), now.plusSeconds(5400), now.plusSeconds(60), new BigDecimal("0.82"),
            "rules-v2", CourierEtaEstimate.Source.HISTORICAL_MODEL, "RECENT_TRANSIT");

        planning.saveEtaEstimate(tenant, first).toCompletableFuture().join();
        planning.saveEtaEstimate(tenant, latest).toCompletableFuture().join();

        assertEquals(latest.id(), planning.findLatestEtaEstimate(tenant, shipment.id()).toCompletableFuture().join().orElseThrow().id());
        assertTrue(planning.findLatestEtaEstimate(other, shipment.id()).toCompletableFuture().join().isEmpty());
    }

    @Test void routePlanAndOrderedStopsRoundTripAtomicallyAndTenantScoped() {
        TenantId tenant = TenantId.generate();
        TenantId other = TenantId.generate();
        Instant now = Instant.parse("2026-10-24T02:00:00Z");
        CourierRoutePlan plan = new CourierRoutePlan(UUID.randomUUID(), tenant, "BDG-01", "courier-01",
            LocalDate.parse("2026-10-24"), 1, CourierRoutePlan.Status.OPTIMIZED, now,
            List.of(new CourierRoutePlan.Stop(1, "pickup-1", CourierRoutePlan.Stop.StopType.PICKUP, now),
                    new CourierRoutePlan.Stop(2, "delivery-1", CourierRoutePlan.Stop.StopType.DELIVERY, now.plusSeconds(900))),
            15000L, 2400);

        planning.saveRoutePlan(tenant, plan).toCompletableFuture().join();

        CourierRoutePlan loaded = planning.findRoutePlan(tenant, plan.id()).toCompletableFuture().join().orElseThrow();
        assertEquals(plan.branchCode(), loaded.branchCode());
        assertEquals(plan.status(), loaded.status());
        assertEquals(List.of("pickup-1", "delivery-1"), loaded.stops().stream().map(CourierRoutePlan.Stop::stopReference).toList());
        assertTrue(planning.findRoutePlan(other, plan.id()).toCompletableFuture().join().isEmpty());
    }

    private static Shipment shipment(TenantId tenant, String reference) {
        Instant now = Instant.parse("2026-10-24T02:00:00Z");
        return Shipment.draft(ShipmentId.generate(), tenant, reference,
            new ShipmentAddress("Sender", "+62111", "Jl A", null, "Bandung", null, null, "ID"),
            new ShipmentAddress("Recipient", "+62222", "Jl B", null, "Jakarta", null, null, "ID"),
            ServiceLevel.STANDARD, now);
    }
}
