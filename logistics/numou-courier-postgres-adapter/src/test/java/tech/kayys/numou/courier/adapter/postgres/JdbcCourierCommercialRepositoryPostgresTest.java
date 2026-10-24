package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcCourierCommercialRepositoryPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcCourierCommercialRepository commercial;
    static JdbcShipmentRepository shipments;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        commercial = new JdbcCourierCommercialRepository(dataSource());
        shipments = new JdbcShipmentRepository(dataSource());
    }

    @Test
    void rateCardsAreTenantScopedAndEffectiveLookupIsDeterministic() {
        TenantId tenant = TenantId.generate();
        TenantId other = TenantId.generate();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        CourierRateCard card = rateCard(tenant, "ID-STD-01", now);

        commercial.saveRateCard(tenant, card).toCompletableFuture().join();
        var found = commercial.findEffectiveRateCard(tenant, ServiceLevel.STANDARD, "bdg", "jkt", now)
                .toCompletableFuture().join();
        assertTrue(found.isPresent());
        assertEquals(card.id(), found.orElseThrow().id());
        assertTrue(commercial.findEffectiveRateCard(other, ServiceLevel.STANDARD, "BDG", "JKT", now)
                .toCompletableFuture().join().isEmpty());

        CompletionException mismatch = assertThrows(CompletionException.class,
                () -> commercial.saveRateCard(other, card).toCompletableFuture().join());
        assertInstanceOf(SecurityException.class, mismatch.getCause());
    }

    @Test
    void quoteSnapshotAndCodSettlementVariancePersistWithoutCrossTenantLinks() throws Exception {
        TenantId tenant = TenantId.generate();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Shipment shipment = shipment(tenant, "COMMERCIAL-01", now);
        shipments.save(tenant, shipment).toCompletableFuture().join();
        CourierRateCard card = rateCard(tenant, "ID-STD-02", now);
        commercial.saveRateCard(tenant, card).toCompletableFuture().join();

        ShipmentQuote quote = ShipmentPricingCalculator.quote(shipment, card, "BDG", "JKT", now, now.plusSeconds(300));
        commercial.saveQuote(tenant, quote).toCompletableFuture().join();
        try (Connection c = dataSource().getConnection();
             PreparedStatement s = c.prepareStatement("SELECT total,rate_card_version FROM courier_shipment_quotes WHERE tenant_id=? AND id=?")) {
            s.setObject(1, tenant.value()); s.setObject(2, quote.id());
            try (ResultSet rs = s.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(quote.total(), rs.getBigDecimal("total"));
                assertEquals(card.version(), rs.getString("rate_card_version"));
            }
        }

        CodObligation cod = new CodObligation(UUID.randomUUID(), tenant, shipment.id(),
                new BigDecimal("250000"), Currency.getInstance("IDR"), now);
        commercial.saveCodObligation(tenant, cod).toCompletableFuture().join();
        cod.collect("courier-17", "BDG-01", new BigDecimal("250000"), now.plusSeconds(60));
        commercial.saveCodObligation(tenant, cod).toCompletableFuture().join();
        cod.settle("closeout-01", new BigDecimal("249000"), now.plusSeconds(120));
        commercial.saveCodObligation(tenant, cod).toCompletableFuture().join();

        try (Connection c = dataSource().getConnection();
             PreparedStatement s = c.prepareStatement("SELECT status,variance FROM courier_cod_obligations WHERE tenant_id=? AND id=?")) {
            s.setObject(1, tenant.value()); s.setObject(2, cod.id());
            try (ResultSet rs = s.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("VARIANCE_RECORDED", rs.getString("status"));
                assertEquals(new BigDecimal("1000.00"), rs.getBigDecimal("variance"));
            }
        }
    }

    @Test
    void codAndQuoteCannotReferenceAnotherTenantsShipment() {
        TenantId owner = TenantId.generate();
        TenantId other = TenantId.generate();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Shipment shipment = shipment(owner, "COMMERCIAL-TENANT-BOUNDARY", now);
        shipments.save(owner, shipment).toCompletableFuture().join();

        ShipmentQuote quote = new ShipmentQuote(UUID.randomUUID(), UUID.randomUUID(), "TARIFF-1", shipment.id(),
                Currency.getInstance("IDR"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, now, null);
        assertThrows(CompletionException.class,
                () -> commercial.saveQuote(other, quote).toCompletableFuture().join());

        CodObligation cod = new CodObligation(UUID.randomUUID(), owner, shipment.id(), BigDecimal.ONE,
                Currency.getInstance("IDR"), now);
        assertThrows(CompletionException.class,
                () -> commercial.saveCodObligation(other, cod).toCompletableFuture().join());
    }

    private static CourierRateCard rateCard(TenantId tenant, String version, Instant now) {
        return new CourierRateCard(UUID.randomUUID(), tenant, version, ServiceLevel.STANDARD, "BDG", "JKT",
                Currency.getInstance("IDR"), new BigDecimal("10000"), new BigDecimal("2000"),
                new BigDecimal("6000"), new BigDecimal("0.11"), now.minusSeconds(60), null);
    }

    private static Shipment shipment(TenantId tenant, String reference, Instant now) {
        Shipment result = Shipment.draft(ShipmentId.generate(), tenant, reference,
                new ShipmentAddress("Sender", "+62111", "Origin Street", null, "Bandung", "West Java", "40111", "ID"),
                new ShipmentAddress("Recipient", "+62222", "Destination Street", null, "Jakarta", "DKI Jakarta", "10110", "ID"),
                ServiceLevel.STANDARD, now);
        result.addParcel(new Parcel(ParcelId.generate(), reference + "-P1", new BigDecimal("1.250"),
                new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("10"), null), now.plusSeconds(1));
        result.book(now.plusSeconds(2));
        return result;
    }

    private static javax.sql.DataSource dataSource() {
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl()); ds.setUser(POSTGRES.getUsername()); ds.setPassword(POSTGRES.getPassword());
        return ds;
    }
}
