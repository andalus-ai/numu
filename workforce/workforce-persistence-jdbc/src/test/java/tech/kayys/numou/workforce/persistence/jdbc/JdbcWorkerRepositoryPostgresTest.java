package tech.kayys.numou.workforce.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.foundation.domain.audit.AuditMeta;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.worker.WorkerStatus;
import tech.kayys.numou.workforce.domain.worker.WorkerType;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JdbcWorkerRepositoryPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcWorkerRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS workforce_workers CASCADE");
            statement.execute(migrationSql());
        }
        repository = new JdbcWorkerRepository(dataSource);
    }

    @Test
    void persistsAndReconstitutesWorkerWithoutEmittingCreationEvent() {
        TenantId tenant = TenantId.generate();
        Worker worker = worker(tenant, UUID.randomUUID());
        repository.save(worker).toCompletableFuture().join();

        Worker restored = repository.findById(tenant, worker.id()).toCompletableFuture().join().orElseThrow();

        assertThat(restored.id()).isEqualTo(worker.id());
        assertThat(restored.tenantId()).isEqualTo(tenant);
        assertThat(restored.person()).isEqualTo(worker.person());
        assertThat(restored.type()).isEqualTo(WorkerType.EMPLOYEE);
        assertThat(restored.status()).isEqualTo(WorkerStatus.ACTIVE);
        assertThat(restored.getVersion()).isZero();
        assertThat(restored.getDomainEvents()).isEmpty();
        assertThat(restored.audit()).isEqualTo(worker.audit());
    }

    @Test
    void tenantScopedLookupDoesNotExposeAnotherTenantsWorker() {
        TenantId owner = TenantId.generate();
        TenantId other = TenantId.generate();
        Worker worker = worker(owner, UUID.randomUUID());
        repository.save(worker).toCompletableFuture().join();

        assertThat(repository.findById(owner, worker.id()).toCompletableFuture().join()).containsInstanceOf(Worker.class);
        assertThat(repository.findById(other, worker.id()).toCompletableFuture().join()).isEmpty();
        assertThat(repository.findByPersonRef(other, worker.person()).toCompletableFuture().join()).isEmpty();
    }

    @Test
    void optimisticLockRejectsStaleAggregateVersion() {
        TenantId tenant = TenantId.generate();
        Worker original = worker(tenant, UUID.randomUUID());
        repository.save(original).toCompletableFuture().join();

        Worker firstRead = repository.findById(tenant, original.id()).toCompletableFuture().join().orElseThrow();
        Worker staleRead = repository.findById(tenant, original.id()).toCompletableFuture().join().orElseThrow();

        firstRead.suspend("operator-a", Instant.parse("2026-10-30T10:00:00Z"), "policy");
        repository.save(firstRead).toCompletableFuture().join();

        staleRead.suspend("operator-b", Instant.parse("2026-10-30T10:01:00Z"), "stale update");
        assertThatThrownBy(() -> repository.save(staleRead).toCompletableFuture().join())
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void allowsSamePersonToHaveIndependentWorkerRecordsAcrossTenants() {
        UUID personId = UUID.randomUUID();
        Worker first = worker(TenantId.generate(), personId);
        Worker second = worker(TenantId.generate(), personId);

        repository.save(first).toCompletableFuture().join();
        repository.save(second).toCompletableFuture().join();

        assertThat(repository.findByPersonRef(first.tenantId(), PersonRef.of(personId)).toCompletableFuture().join()).isPresent();
        assertThat(repository.findByPersonRef(second.tenantId(), PersonRef.of(personId)).toCompletableFuture().join()).isPresent();
    }

    private static Worker worker(TenantId tenant, UUID personId) {
        Instant now = Instant.parse("2026-10-30T09:00:00Z");
        return Worker.create(WorkerId.generate(), tenant, PersonRef.of(personId), WorkerType.EMPLOYEE, "test", now);
    }

    private static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static String migrationSql() throws IOException {
        try (InputStream input = JdbcWorkerRepositoryPostgresTest.class.getResourceAsStream(
                "/db/migration/V1__workforce_worker.sql")) {
            if (input == null) {
                throw new IllegalStateException("Workforce worker migration resource is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
