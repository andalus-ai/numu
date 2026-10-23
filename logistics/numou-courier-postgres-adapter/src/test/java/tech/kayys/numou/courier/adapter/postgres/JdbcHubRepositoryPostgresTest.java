package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.courier.domain.*;
import java.math.BigDecimal;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHubRepositoryPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcHubRepository repository;
    static JdbcShipmentRepository shipmentRepository;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        repository = new JdbcHubRepository(dataSource());
        shipmentRepository = new JdbcShipmentRepository(dataSource());
    }

    @Test
    void tenantCannotReadOrDeleteAnotherTenantsHub() {
        TenantId owner = TenantId.generate();
        TenantId otherTenant = TenantId.generate();
        Hub hub = hub(owner, "JKT-01");

        repository.save(owner, hub).toCompletableFuture().join();

        assertTrue(repository.findById(otherTenant, hub.id()).toCompletableFuture().join().isEmpty());
        repository.delete(otherTenant, hub.id()).toCompletableFuture().join();
        assertTrue(repository.findById(owner, hub.id()).toCompletableFuture().join().isPresent());
    }

    @Test
    void hubCodesAreUniqueWithinATenantButMayRepeatAcrossTenants() {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        repository.save(tenantA, hub(tenantA, "DEPOT-01")).toCompletableFuture().join();
        repository.save(tenantB, hub(tenantB, "DEPOT-01")).toCompletableFuture().join();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> repository.save(tenantA, hub(tenantA, "DEPOT-01")).toCompletableFuture().join());

        assertInstanceOf(JdbcHubRepository.HubCodeConflictException.class, failure.getCause());
    }

    @Test
    void concurrentDuplicateCodesAllowExactlyOneInsert() {
        TenantId tenant = TenantId.generate();
        CompletableFuture<Throwable> first = CompletableFuture.supplyAsync(
                () -> repository.save(tenant, hub(tenant, "RACE-01")).toCompletableFuture()
                        .handle((saved, failure) -> failure).join());
        CompletableFuture<Throwable> second = CompletableFuture.supplyAsync(
                () -> repository.save(tenant, hub(tenant, "RACE-01")).toCompletableFuture()
                        .handle((saved, failure) -> failure).join());

        CompletableFuture.allOf(first, second).join();
        Throwable firstFailure = first.join();
        Throwable secondFailure = second.join();

        assertNotEquals(firstFailure == null, secondFailure == null,
                "exactly one insert should succeed");
        Throwable failure = firstFailure == null ? secondFailure : firstFailure;
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        assertInstanceOf(JdbcHubRepository.HubCodeConflictException.class, failure);
    }

    @Test
    void shipmentAndParcelsRoundTripAtomicallyAndRemainTenantScoped() {
        TenantId owner = TenantId.generate();
        TenantId other = TenantId.generate();
        Shipment shipment = shipment(owner, "SHIP-ROUNDTRIP");
        shipment.addParcel(parcel("PIECE-01"), Instant.now().plusSeconds(1));
        shipment.addParcel(parcel("PIECE-02"), Instant.now().plusSeconds(2));
        shipment.book(Instant.now().plusSeconds(3));

        shipmentRepository.save(owner, shipment).toCompletableFuture().join();

        assertTrue(shipmentRepository.findById(other, shipment.id()).toCompletableFuture().join().isEmpty());
        Shipment loaded = shipmentRepository.findByReference(owner, "ship-roundtrip")
                .toCompletableFuture().join().orElseThrow();
        assertEquals(ShipmentStatus.BOOKED, loaded.status());
        assertEquals(2, loaded.parcels().size());
        assertEquals(shipment.sender(), loaded.sender());
        assertEquals(shipment.bookedAt(), loaded.bookedAt());
    }

    @Test
    void shipmentReferenceIsUniquePerTenantNotGlobally() {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        shipmentRepository.save(tenantA, shipment(tenantA, "SHIP-UNIQUE")).toCompletableFuture().join();
        shipmentRepository.save(tenantB, shipment(tenantB, "SHIP-UNIQUE")).toCompletableFuture().join();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> shipmentRepository.save(tenantA, shipment(tenantA, "SHIP-UNIQUE"))
                        .toCompletableFuture().join());
        assertInstanceOf(JdbcShipmentRepository.ShipmentReferenceConflictException.class, failure.getCause());
    }

    private static Shipment shipment(TenantId tenantId, String reference) {
        ShipmentAddress sender = new ShipmentAddress("Sender", "+62111", "Origin Street", null,
                "Bandung", "West Java", "40111", "ID");
        ShipmentAddress recipient = new ShipmentAddress("Recipient", "+62222", "Destination Street", null,
                "Jakarta", "DKI Jakarta", "10110", "ID");
        return Shipment.draft(ShipmentId.generate(), tenantId, reference,
                sender, recipient, ServiceLevel.STANDARD, Instant.now());
    }

    private static Parcel parcel(String reference) {
        return new Parcel(ParcelId.generate(), reference, new BigDecimal("1.250"),
                new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("10"), "Keep dry");
    }

    private static Hub hub(TenantId tenantId, String code) {
        return Hub.create(HubId.generate(), tenantId, OrganizationId.generate(), OrganizationUnitId.generate(),
                code, "Test Hub", HubType.ORIGIN_HUB,
                new HubAddress("1 Test Street", null, "Jakarta", null, null, "ID", -6.2, 106.8),
                ZoneId.of("Asia/Jakarta"), Instant.now());
    }

    private static javax.sql.DataSource dataSource() {
        org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
