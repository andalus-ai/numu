package tech.kayys.numou.warehouse.persistence.jdbc;

import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditArchiveManifest;
import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditPersistenceException;
import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditArchiveManifestRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Objects;

/** PostgreSQL JDBC persistence for immutable, tenant-scoped recovery audit archive manifests. */
public final class JdbcWarehouseRecoveryAuditArchiveManifestRepository
        implements WarehouseRecoveryAuditArchiveManifestRepository {
    private final DataSource dataSource;

    public JdbcWarehouseRecoveryAuditArchiveManifestRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void insert(WarehouseRecoveryAuditArchiveManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        String sql = "INSERT INTO warehouse_recovery_audit_archive_manifest "
                + "(export_id, tenant_id, artifact_location, sha256_hex, byte_size, event_count, created_at, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, manifest.exportId());
            statement.setString(2, manifest.tenantId());
            statement.setString(3, manifest.artifactLocation());
            statement.setString(4, manifest.sha256Hex());
            statement.setLong(5, manifest.byteSize());
            statement.setLong(6, manifest.eventCount());
            statement.setTimestamp(7, Timestamp.from(manifest.createdAt()));
            statement.setString(8, manifest.createdBy());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not persist recovery audit archive manifest", exception);
        }
    }

    @Override
    public Optional<WarehouseRecoveryAuditArchiveManifest> find(String tenantId, String exportId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (exportId == null || exportId.isBlank()) throw new IllegalArgumentException("exportId is required");
        String sql = "SELECT export_id, tenant_id, artifact_location, sha256_hex, byte_size, event_count, created_at, created_by "
                + "FROM warehouse_recovery_audit_archive_manifest WHERE tenant_id = ? AND export_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId.trim());
            statement.setString(2, exportId.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new WarehouseRecoveryAuditArchiveManifest(
                        result.getString("export_id"), result.getString("tenant_id"),
                        result.getString("artifact_location"), result.getString("sha256_hex"),
                        result.getLong("byte_size"), result.getLong("event_count"),
                        result.getTimestamp("created_at").toInstant(), result.getString("created_by")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read recovery audit archive manifest", exception);
        }
    }
}
