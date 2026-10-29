package tech.kayys.numou.pos.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.pos.application.port.out.SaleCommandIdempotency;
import tech.kayys.numou.pos.application.port.out.SaleRepository.VersionedSale;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Durable idempotency adapter. The reservation, sale mutation performed by the
 * supplier, and saved result participate in one JTA transaction.
 */
@ApplicationScoped
public class PostgresSaleCommandIdempotency implements SaleCommandIdempotency {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public PostgresSaleCommandIdempotency(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public VersionedSale execute(UUID tenantId, UUID actorId, String idempotencyKey,
            String operation, String requestFingerprint, Supplier<VersionedSale> command) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(actorId, "actorId cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint cannot be null");
        Objects.requireNonNull(command, "command cannot be null");
        if (!requestFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestFingerprint must be a lowercase SHA-256 hex digest");
        }

        String reserveSql = "INSERT INTO pos_command_idempotency "
                + "(tenant_id, actor_id, idempotency_key, operation, request_fingerprint, command_status) "
                + "VALUES (?, ?, ?, ?, ?, 'PROCESSING') ON CONFLICT DO NOTHING";
        try (Connection connection = dataSource.getConnection()) {
            boolean reserved;
            try (PreparedStatement reserve = connection.prepareStatement(reserveSql)) {
                reserve.setObject(1, tenantId);
                reserve.setObject(2, actorId);
                reserve.setString(3, idempotencyKey);
                reserve.setString(4, operation);
                reserve.setString(5, requestFingerprint);
                reserved = reserve.executeUpdate() == 1;
            }

            if (!reserved) {
                StoredCommand stored = loadForUpdate(connection, tenantId, actorId, idempotencyKey);
                if (stored == null) {
                    throw new PostgresSaleRepository.PosStorageException(
                            "Idempotency reservation disappeared during command execution", null);
                }
                if (!stored.operation().equals(operation)
                        || !stored.fingerprint().equals(requestFingerprint)) {
                    throw new IdempotencyConflictException(tenantId, actorId, idempotencyKey);
                }
                if (!"COMPLETED".equals(stored.status()) || stored.resultPayload() == null) {
                    throw new IncompleteIdempotencyRecordException(tenantId, actorId, idempotencyKey);
                }
                return readResult(stored.resultPayload());
            }

            VersionedSale result = Objects.requireNonNull(command.get(), "command result cannot be null");
            String completeSql = "UPDATE pos_command_idempotency SET command_status = 'COMPLETED', "
                    + "result_payload = ?::jsonb, completed_at = CURRENT_TIMESTAMP "
                    + "WHERE tenant_id = ? AND actor_id = ? AND idempotency_key = ? "
                    + "AND command_status = 'PROCESSING'";
            try (PreparedStatement complete = connection.prepareStatement(completeSql)) {
                complete.setString(1, writeJson(result));
                complete.setObject(2, tenantId);
                complete.setObject(3, actorId);
                complete.setString(4, idempotencyKey);
                if (complete.executeUpdate() != 1) {
                    throw new PostgresSaleRepository.PosStorageException(
                            "Unable to complete idempotency record", null);
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new PostgresSaleRepository.PosStorageException("Unable to execute idempotent POS command", exception);
        }
    }

    private StoredCommand loadForUpdate(Connection connection, UUID tenantId, UUID actorId, String key)
            throws SQLException {
        String sql = "SELECT operation, request_fingerprint, command_status, result_payload "
                + "FROM pos_command_idempotency WHERE tenant_id = ? AND actor_id = ? "
                + "AND idempotency_key = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, actorId);
            statement.setString(3, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new StoredCommand(result.getString("operation"),
                        result.getString("request_fingerprint").trim(),
                        result.getString("command_status"), result.getString("result_payload"));
            }
        }
    }

    private VersionedSale readResult(String json) {
        try {
            return objectMapper.readValue(json, VersionedSale.class);
        } catch (JsonProcessingException exception) {
            throw new PostgresSaleRepository.PosStorageException(
                    "Stored idempotency result payload is invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new PostgresSaleRepository.PosStorageException(
                    "Unable to serialize idempotency result", exception);
        }
    }

    private record StoredCommand(String operation, String fingerprint, String status, String resultPayload) {}

    public static final class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(UUID tenantId, UUID actorId, String key) {
            super("Idempotency key reused with different request for tenant " + tenantId
                    + ", actor " + actorId + ", key " + key);
        }
    }

    public static final class IncompleteIdempotencyRecordException extends RuntimeException {
        public IncompleteIdempotencyRecordException(UUID tenantId, UUID actorId, String key) {
            super("Idempotency record is incomplete and requires operational recovery for tenant "
                    + tenantId + ", actor " + actorId + ", key " + key);
        }
    }
}
