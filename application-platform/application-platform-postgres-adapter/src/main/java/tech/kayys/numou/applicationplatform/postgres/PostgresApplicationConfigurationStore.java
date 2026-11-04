package tech.kayys.numou.applicationplatform.postgres;

import tech.kayys.numou.applicationplatform.application.configuration.ApplicationConfigurationStore;
import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * PostgreSQL implementation of tenant-scoped, revisioned application configuration.
 * Each write serializes on a transaction-scoped advisory lock and atomically updates
 * the current snapshot and appends its immutable revision.
 */
public final class PostgresApplicationConfigurationStore implements ApplicationConfigurationStore {
    private static final String SELECT_CURRENT = """
            SELECT schema_version, revision, payload_json, payload_sha256, updated_by, updated_at
            FROM application_configurations
            WHERE tenant_id=? AND application_id=?
            """;

    private final DataSource dataSource;

    public PostgresApplicationConfigurationStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public CompletionStage<Optional<ApplicationConfigurationSnapshot>> findCurrent(
            TenantId tenantId, String applicationId) {
        try {
            Objects.requireNonNull(tenantId, "tenantId");
            ApplicationId id = new ApplicationId(applicationId);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    setTenant(connection, tenantId);
                    Optional<ApplicationConfigurationSnapshot> current = selectCurrent(connection, tenantId, id);
                    connection.commit();
                    return CompletableFuture.completedFuture(current);
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("find application configuration", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletionStage<SaveResult> save(ApplicationConfigurationSnapshot snapshot, long expectedRevision) {
        try {
            Objects.requireNonNull(snapshot, "snapshot");
            if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision cannot be negative");
            if (snapshot.revision() != Math.addExact(expectedRevision, 1))
                throw new IllegalArgumentException("snapshot revision must be expectedRevision + 1");

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    setTenant(connection, snapshot.tenantId());
                    lockConfiguration(connection, snapshot.tenantId(), snapshot.applicationId());
                    Optional<ApplicationConfigurationSnapshot> current =
                            selectCurrent(connection, snapshot.tenantId(), snapshot.applicationId());
                    long actualRevision = current.map(ApplicationConfigurationSnapshot::revision).orElse(0L);
                    if (actualRevision != expectedRevision) {
                        connection.commit();
                        return CompletableFuture.completedFuture(new SaveResult(false, current));
                    }

                    if (current.isEmpty()) insertCurrent(connection, snapshot);
                    else updateCurrent(connection, snapshot, expectedRevision);
                    appendRevision(connection, snapshot);
                    connection.commit();
                    return CompletableFuture.completedFuture(
                            new SaveResult(true, Optional.of(snapshot)));
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("save application configuration", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void setTenant(Connection connection, TenantId tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, tenantId.value().toString());
            statement.execute();
        }
    }

    private static void lockConfiguration(Connection connection, TenantId tenantId, ApplicationId applicationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, tenantId.value() + ":" + applicationId.value());
            statement.execute();
        }
    }

    private static Optional<ApplicationConfigurationSnapshot> selectCurrent(
            Connection connection, TenantId tenantId, ApplicationId applicationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CURRENT)) {
            statement.setObject(1, tenantId.value());
            statement.setString(2, applicationId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new ApplicationConfigurationSnapshot(
                        tenantId, applicationId, result.getString("schema_version"),
                        result.getLong("revision"), result.getString("payload_json"),
                        result.getString("payload_sha256"), result.getString("updated_by"),
                        result.getTimestamp("updated_at").toInstant()));
            }
        }
    }

    private static void insertCurrent(Connection connection, ApplicationConfigurationSnapshot snapshot)
            throws SQLException {
        String sql = """
                INSERT INTO application_configurations
                    (tenant_id, application_id, schema_version, revision, payload_json,
                     payload_sha256, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSnapshot(statement, snapshot);
            statement.executeUpdate();
        }
    }

    private static void updateCurrent(Connection connection, ApplicationConfigurationSnapshot snapshot,
            long expectedRevision) throws SQLException {
        String sql = """
                UPDATE application_configurations
                SET schema_version=?, revision=?, payload_json=?, payload_sha256=?, updated_by=?, updated_at=?
                WHERE tenant_id=? AND application_id=? AND revision=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.schemaVersion());
            statement.setLong(2, snapshot.revision());
            statement.setString(3, snapshot.payloadJson());
            statement.setString(4, snapshot.payloadSha256());
            statement.setString(5, snapshot.updatedBy());
            statement.setTimestamp(6, Timestamp.from(snapshot.updatedAt()));
            statement.setObject(7, snapshot.tenantId().value());
            statement.setString(8, snapshot.applicationId().value());
            statement.setLong(9, expectedRevision);
            if (statement.executeUpdate() != 1)
                throw new SQLException("configuration compare-and-set lost after lock", "40001");
        }
    }

    private static void appendRevision(Connection connection, ApplicationConfigurationSnapshot snapshot)
            throws SQLException {
        String sql = """
                INSERT INTO application_configuration_revisions
                    (tenant_id, application_id, revision, schema_version, payload_json,
                     payload_sha256, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, snapshot.tenantId().value());
            statement.setString(2, snapshot.applicationId().value());
            statement.setLong(3, snapshot.revision());
            statement.setString(4, snapshot.schemaVersion());
            statement.setString(5, snapshot.payloadJson());
            statement.setString(6, snapshot.payloadSha256());
            statement.setString(7, snapshot.updatedBy());
            statement.setTimestamp(8, Timestamp.from(snapshot.updatedAt()));
            statement.executeUpdate();
        }
    }

    private static void bindSnapshot(PreparedStatement statement, ApplicationConfigurationSnapshot snapshot)
            throws SQLException {
        statement.setObject(1, snapshot.tenantId().value());
        statement.setString(2, snapshot.applicationId().value());
        statement.setString(3, snapshot.schemaVersion());
        statement.setLong(4, snapshot.revision());
        statement.setString(5, snapshot.payloadJson());
        statement.setString(6, snapshot.payloadSha256());
        statement.setString(7, snapshot.updatedBy());
        statement.setTimestamp(8, Timestamp.from(snapshot.updatedAt()));
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static RuntimeException storageFailure(String operation, SQLException failure) {
        return new IllegalStateException("Failed to " + operation + " (SQLState="
                + failure.getSQLState() + ")", failure);
    }
}
