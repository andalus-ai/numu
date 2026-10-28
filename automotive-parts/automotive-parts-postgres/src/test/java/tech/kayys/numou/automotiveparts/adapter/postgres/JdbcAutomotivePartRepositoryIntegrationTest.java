package tech.kayys.numou.automotiveparts.adapter.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.automotiveparts.domain.AutomotivePartProfile;
import tech.kayys.numou.automotiveparts.domain.PartFitment;
import tech.kayys.numou.automotiveparts.domain.PartReference;
import tech.kayys.numou.automotiveparts.domain.PartReferenceType;
import tech.kayys.numou.automotiveparts.domain.VehicleFitment;
import tech.kayys.numou.automotiveparts.domain.VehicleSpecification;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAutomotivePartRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcAutomotivePartRepository repository;

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        repository = new JdbcAutomotivePartRepository(dataSource);
    }

    @Test
    void referencesAreUniqueWithinTenantNamespaceButIsolatedAcrossTenants() {
        ProductId first = ProductId.generate();
        repository.saveProfile(profile("tenant-a", first,
                new PartReference(PartReferenceType.OEM, "toy-123")));
        assertEquals(first, repository.findByReference("tenant-a",
                new PartReference(PartReferenceType.OEM, "TOY-123")).orElseThrow().productId());
        assertTrue(repository.findByReference("tenant-b",
                new PartReference(PartReferenceType.OEM, "TOY-123")).isEmpty());

        repository.saveProfile(profile("tenant-b", ProductId.generate(),
                new PartReference(PartReferenceType.OEM, "TOY-123")));
        assertThrows(IllegalStateException.class, () -> repository.saveProfile(profile("tenant-a",
                ProductId.generate(), new PartReference(PartReferenceType.OEM, "TOY-123"))));
        assertEquals(first, repository.findProfile("tenant-a", first).orElseThrow().productId());
    }

    @Test
    void fitmentSearchHonorsTenantVehicleAndEffectiveDate() {
        ProductId product = ProductId.generate();
        repository.saveProfile(profile("tenant-a", product, new PartReference(PartReferenceType.OEM, "FILTER-9")));
        repository.saveFitment(new PartFitment("tenant-a", product, new VehicleFitment(
                "tenant-a", "ID", "TOYOTA", "AVANZA", "GEN-2", 2020, 2024,
                "2NR", null, "OEM_CATALOG", "catalog-row-77",
                LocalDate.of(2023, 1, 1), LocalDate.of(2025, 12, 31))));

        var matches = repository.findFitmentCandidates("tenant-a",
                new VehicleSpecification("ID", "Toyota", "Avanza", "GEN-2", 2022, "2NR", null),
                LocalDate.of(2024, 6, 1));
        assertEquals(1, matches.size());
        assertEquals(product, matches.getFirst().productId());
        assertTrue(repository.findFitmentCandidates("tenant-b",
                new VehicleSpecification("ID", "Toyota", "Avanza", "GEN-2", 2022, "2NR", null),
                LocalDate.of(2024, 6, 1)).isEmpty());
        assertTrue(repository.findFitmentCandidates("tenant-a",
                new VehicleSpecification("ID", "Toyota", "Avanza", "GEN-2", 2022, "2NR", null),
                LocalDate.of(2026, 1, 1)).isEmpty());
    }

    private static AutomotivePartProfile profile(String tenant, ProductId id, PartReference reference) {
        return new AutomotivePartProfile(tenant, id,
                AutomotivePartProfile.PartKind.REPLACEMENT_PART, "Workshop replacement part", List.of(reference));
    }
}
