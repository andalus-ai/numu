package tech.kayys.numou.workforce.persistence.jdbc;

import tech.kayys.numou.foundation.domain.audit.AuditMeta;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.worker.WorkerStatus;
import tech.kayys.numou.workforce.domain.worker.WorkerType;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * PostgreSQL JDBC implementation of the Worker persistence port.
 * Tenant-aware methods always constrain the SQL predicate by tenant_id.
 */
public final class JdbcWorkerRepository implements WorkerRepository {
    private static final String COLUMNS = "id, tenant_id, person_id, worker_type, status, created_by, created_at, updated_by, updated_at, version";
    private final DataSource dataSource;

    public JdbcWorkerRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public CompletionStage<Worker> save(Worker worker) {
        Objects.requireNonNull(worker, "worker");
        String insert = "INSERT INTO workforce_workers (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)";
        String update = "UPDATE workforce_workers SET tenant_id=?, person_id=?, worker_type=?, status=?, updated_by=?, updated_at=?, version=? WHERE tenant_id=? AND id=? AND version=?";
        try (Connection c = dataSource.getConnection()) {
            if (worker.getVersion() == 0) {
                try (PreparedStatement p = c.prepareStatement(insert)) {
                    bindInsert(p, worker);
                    p.executeUpdate();
                }
            } else {
                try (PreparedStatement p = c.prepareStatement(update)) {
                    p.setObject(1, worker.tenantId().value());
                    p.setObject(2, worker.person().value());
                    p.setString(3, worker.type().name());
                    p.setString(4, worker.status().name());
                    p.setString(5, worker.audit().updatedBy());
                    p.setTimestamp(6, Timestamp.from(worker.audit().updatedAt()));
                    p.setInt(7, worker.getVersion());
                    p.setObject(8, worker.tenantId().value());
                    p.setObject(9, worker.id().value());
                    p.setInt(10, worker.getVersion() - 1);
                    if (p.executeUpdate() != 1) {
                        throw new IllegalStateException("Worker was concurrently modified or does not exist");
                    }
                }
            }
            return CompletableFuture.completedFuture(worker);
        } catch (SQLException e) {
            throw failure("save worker", e);
        }
    }

    @Override
    public CompletionStage<Optional<Worker>> findById(WorkerId id) {
        Objects.requireNonNull(id, "id");
        return queryOne("SELECT " + COLUMNS + " FROM workforce_workers WHERE id=?", p -> p.setObject(1, id.value()));
    }

    @Override
    public CompletionStage<Optional<Worker>> findById(TenantId tenantId, WorkerId workerId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workerId, "workerId");
        return queryOne("SELECT " + COLUMNS + " FROM workforce_workers WHERE tenant_id=? AND id=?", p -> {
            p.setObject(1, tenantId.value());
            p.setObject(2, workerId.value());
        });
    }

    @Override
    public CompletionStage<Optional<Worker>> findByPersonRef(TenantId tenantId, PersonRef personRef) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(personRef, "personRef");
        return queryOne("SELECT " + COLUMNS + " FROM workforce_workers WHERE tenant_id=? AND person_id=? ORDER BY created_at, id LIMIT 1", p -> {
            p.setObject(1, tenantId.value());
            p.setObject(2, personRef.value());
        });
    }

    @Override
    public CompletionStage<Boolean> existsById(WorkerId id) {
        Objects.requireNonNull(id, "id");
        String sql = "SELECT 1 FROM workforce_workers WHERE id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, id.value());
            try (ResultSet r = p.executeQuery()) {
                return CompletableFuture.completedFuture(r.next());
            }
        } catch (SQLException e) {
            throw failure("check worker existence", e);
        }
    }

    @Override
    public CompletionStage<Void> delete(Worker worker) {
        Objects.requireNonNull(worker, "worker");
        return deleteById(worker.id());
    }

    @Override
    public CompletionStage<Void> deleteById(WorkerId id) {
        Objects.requireNonNull(id, "id");
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement("DELETE FROM workforce_workers WHERE id=?")) {
            p.setObject(1, id.value());
            p.executeUpdate();
            return CompletableFuture.completedFuture(null);
        } catch (SQLException e) {
            throw failure("delete worker", e);
        }
    }

    private CompletionStage<Optional<Worker>> queryOne(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            binder.bind(p);
            try (ResultSet r = p.executeQuery()) {
                return CompletableFuture.completedFuture(r.next() ? Optional.of(map(r)) : Optional.empty());
            }
        } catch (SQLException e) {
            throw failure("read worker", e);
        }
    }

    private static void bindInsert(PreparedStatement p, Worker w) throws SQLException {
        p.setObject(1, w.id().value());
        p.setObject(2, w.tenantId().value());
        p.setObject(3, w.person().value());
        p.setString(4, w.type().name());
        p.setString(5, w.status().name());
        p.setString(6, w.audit().createdBy());
        p.setTimestamp(7, Timestamp.from(w.audit().createdAt()));
        p.setString(8, w.audit().updatedBy());
        p.setTimestamp(9, Timestamp.from(w.audit().updatedAt()));
        p.setInt(10, w.getVersion());
    }

    private static Worker map(ResultSet r) throws SQLException {
        UUID id = r.getObject("id", UUID.class);
        TenantId tenant = TenantId.of(r.getObject("tenant_id", UUID.class));
        PersonRef person = PersonRef.of(r.getObject("person_id", UUID.class));
        WorkerType type = WorkerType.valueOf(r.getString("worker_type"));
        WorkerStatus status = WorkerStatus.valueOf(r.getString("status"));
        AuditMeta audit = new AuditMeta(
                r.getString("created_by"), r.getTimestamp("created_at").toInstant(),
                r.getString("updated_by"), r.getTimestamp("updated_at").toInstant());
        return Worker.reconstitute(WorkerId.of(id), tenant, person, type, status, audit, r.getInt("version"));
    }

    private static IllegalStateException failure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation + ": " + cause.getMessage(), cause);
    }

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
}
