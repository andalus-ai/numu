package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionStore;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionStore.Command;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionStore.Result;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** PostgreSQL adapter for immutable, tenant-scoped discrepancy resolution evidence. */
@ApplicationScoped
public class JdbcDispatchWaveDiscrepancyResolutionStore implements DispatchWaveDiscrepancyResolutionStore {
    private final DataSource dataSource;

    @Inject
    public JdbcDispatchWaveDiscrepancyResolutionStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public CompletionStage<Result> resolve(Command command) {
        if (command == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("command must not be null"));
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO courier_dispatch_wave_discrepancy_resolutions
                          (tenant_id,id,wave_id,discrepancy_id,parcel_reference,resolution_code,reason,
                           actor_reference,permission_reference,evidence_reference,source,idempotency_key,
                           payload_fingerprint,occurred_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (tenant_id,source,idempotency_key) DO NOTHING
                        """)) {
                    statement.setObject(1, command.tenantId().value());
                    statement.setObject(2, command.resolutionId());
                    statement.setObject(3, command.waveId());
                    statement.setObject(4, command.discrepancyId());
                    statement.setString(5, command.parcelReference());
                    statement.setString(6, command.resolutionCode());
                    statement.setString(7, command.reason());
                    statement.setString(8, command.actorReference());
                    statement.setString(9, command.permissionReference());
                    statement.setString(10, command.evidenceReference());
                    statement.setString(11, command.source());
                    statement.setString(12, command.idempotencyKey());
                    statement.setString(13, command.payloadFingerprint());
                    statement.setTimestamp(14, Timestamp.from(command.occurredAt()));
                    inserted = statement.executeUpdate();
                }

                if (inserted == 1) {
                    connection.commit();
                    return CompletableFuture.completedFuture(
                            new Result(Outcome.ACCEPTED, command.resolutionId().toString()));
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id, payload_fingerprint
                        FROM courier_dispatch_wave_discrepancy_resolutions
                        WHERE tenant_id=? AND source=? AND idempotency_key=?
                        FOR SHARE
                        """)) {
                    statement.setObject(1, command.tenantId().value());
                    statement.setString(2, command.source());
                    statement.setString(3, command.idempotencyKey());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("idempotency conflict row disappeared");
                        String reference = result.getObject("id").toString();
                        Outcome outcome = command.payloadFingerprint().equals(result.getString("payload_fingerprint"))
                                ? Outcome.REPLAYED : Outcome.IDEMPOTENCY_CONFLICT;
                        connection.commit();
                        return CompletableFuture.completedFuture(new Result(outcome, reference));
                    }
                }
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Unable to persist discrepancy resolution", e));
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Unable to open discrepancy resolution transaction", e));
        }
    }
}