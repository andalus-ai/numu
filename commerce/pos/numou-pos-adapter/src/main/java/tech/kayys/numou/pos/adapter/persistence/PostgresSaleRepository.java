package tech.kayys.numou.pos.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.pos.application.port.out.SaleRepository;
import tech.kayys.numou.pos.domain.Sale;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter. Calls participate in the caller's JTA transaction. */
@ApplicationScoped
public class PostgresSaleRepository implements SaleRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public PostgresSaleRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<VersionedSale> find(UUID tenantId, UUID saleId) {
        String sql = "SELECT sale_payload, version FROM pos_sale WHERE tenant_id = ? AND sale_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, saleId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new VersionedSale(readSale(result.getString("sale_payload")),
                        result.getLong("version")));
            }
        } catch (SQLException exception) {
            throw new PosStorageException("Unable to load POS sale", exception);
        }
    }

    @Override
    @Transactional
    public VersionedSale create(Sale sale) {
        String sql = "INSERT INTO pos_sale (tenant_id, sale_id, version, sale_payload) VALUES (?, ?, 0, ?::jsonb)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sale.tenantId());
            statement.setObject(2, sale.id());
            statement.setString(3, writeJson(sale));
            statement.executeUpdate();
            return new VersionedSale(sale, 0);
        } catch (SQLException exception) {
            throw new PosStorageException("Unable to create POS sale; identifier may already exist", exception);
        }
    }

    @Override
    @Transactional
    public VersionedSale update(Sale sale, long expectedVersion) {
        String sql = "UPDATE pos_sale SET sale_payload = ?::jsonb, version = version + 1, "
                + "updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND sale_id = ? AND version = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, writeJson(sale));
            statement.setObject(2, sale.tenantId());
            statement.setObject(3, sale.id());
            statement.setLong(4, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new OptimisticSaleConflictException(sale.tenantId(), sale.id(), expectedVersion);
            }
            return new VersionedSale(sale, expectedVersion + 1);
        } catch (SQLException exception) {
            throw new PosStorageException("Unable to update POS sale", exception);
        }
    }

    private Sale readSale(String json) {
        try {
            return objectMapper.readValue(json, Sale.class);
        } catch (JsonProcessingException exception) {
            throw new PosStorageException("Stored POS sale payload is invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new PosStorageException("Unable to serialize POS sale payload", exception);
        }
    }

    public static final class OptimisticSaleConflictException extends RuntimeException {
        public OptimisticSaleConflictException(UUID tenantId, UUID saleId, long expectedVersion) {
            super("POS sale version conflict for tenant " + tenantId + ", sale " + saleId
                    + ", expected version " + expectedVersion);
        }
    }

    public static final class PosStorageException extends RuntimeException {
        public PosStorageException(String message, Throwable cause) { super(message, cause); }
    }
}
