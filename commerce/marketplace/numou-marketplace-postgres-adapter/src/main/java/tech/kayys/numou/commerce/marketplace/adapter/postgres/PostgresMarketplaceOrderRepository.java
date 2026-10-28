package tech.kayys.numou.commerce.marketplace.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.application.port.IdempotentMarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.application.MarketplaceOrderIdempotencyConflictException;
import tech.kayys.numou.commerce.marketplace.application.MarketplaceOrderRequestFingerprint;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable tenant-scoped PostgreSQL adapter for marketplace orders.
 *
 * <p>Order header and immutable commercial lines are inserted in one transaction.
 * Lifecycle transitions use a conditional update on tenant, order id, and expected
 * status, so concurrent application instances cannot silently overwrite each other.</p>
 */
@ApplicationScoped
public class PostgresMarketplaceOrderRepository implements MarketplaceOrderRepository, IdempotentMarketplaceOrderRepository {
    private final DataSource dataSource;

    @Inject
    public PostgresMarketplaceOrderRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public void create(MarketplaceOrder order) {
        Objects.requireNonNull(order, "order is required");
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO marketplace_order
                        (order_id, tenant_id, buyer_id, currency, order_status,
                         cancellation_reason, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """)) {
                statement.setObject(1, order.id());
                statement.setString(2, order.tenantId());
                statement.setString(3, order.buyerId());
                statement.setString(4, order.currency());
                statement.setString(5, order.status().name());
                statement.setString(6, order.cancellationReason());
                statement.setTimestamp(7, Timestamp.from(order.createdAt()));
                statement.setTimestamp(8, Timestamp.from(order.updatedAt()));
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO marketplace_order_line
                        (tenant_id, order_id, line_index, line_id, product_id, offering_id,
                         seller_id, description, quantity, unit, unit_price, currency)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (int i = 0; i < order.lines().size(); i++) {
                    MarketplaceOrderLine line = order.lines().get(i);
                    statement.setString(1, order.tenantId());
                    statement.setObject(2, order.id());
                    statement.setInt(3, i);
                    statement.setString(4, line.lineId());
                    statement.setString(5, line.productId());
                    statement.setString(6, line.offeringId());
                    statement.setString(7, line.sellerId());
                    statement.setString(8, line.description());
                    statement.setBigDecimal(9, line.quantity());
                    statement.setString(10, line.unit());
                    statement.setBigDecimal(11, line.unitPrice());
                    statement.setString(12, line.currency());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }


    @Override
    public MarketplaceOrder createOrGet(String tenantId, String idempotencyKey,
                                        String requestFingerprint, MarketplaceOrder proposedOrder) {
        requireTenant(tenantId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestFingerprint must be a lowercase SHA-256 hex digest");
        }
        Objects.requireNonNull(proposedOrder, "proposedOrder is required");
        if (!tenantId.equals(proposedOrder.tenantId())) {
            throw new IllegalArgumentException("Order tenant must match idempotency scope");
        }
        String calculatedFingerprint = MarketplaceOrderRequestFingerprint.calculate(
                tenantId, proposedOrder.buyerId(), proposedOrder.currency(), proposedOrder.lines());
        if (!requestFingerprint.equals(calculatedFingerprint)) {
            throw new IllegalArgumentException("Fingerprint does not match the proposed order snapshot");
        }

        return inTransaction(connection -> {
            int claimed;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO marketplace_order_idempotency
                        (tenant_id, idempotency_key, request_fingerprint, order_id, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                    """)) {
                statement.setString(1, tenantId);
                statement.setString(2, idempotencyKey);
                statement.setString(3, requestFingerprint);
                statement.setObject(4, proposedOrder.id());
                statement.setTimestamp(5, Timestamp.from(proposedOrder.createdAt()));
                claimed = statement.executeUpdate();
            }

            if (claimed == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT request_fingerprint, order_id
                        FROM marketplace_order_idempotency
                        WHERE tenant_id = ? AND idempotency_key = ?
                        FOR SHARE
                        """)) {
                    statement.setString(1, tenantId);
                    statement.setString(2, idempotencyKey);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Idempotency claim disappeared during lookup");
                        }
                        if (!requestFingerprint.equals(result.getString("request_fingerprint"))) {
                            throw new MarketplaceOrderIdempotencyConflictException(
                                    "Idempotency key was already used for a different marketplace order request");
                        }
                        UUID existingOrderId = result.getObject("order_id", UUID.class);
                        return loadOrder(connection, tenantId, existingOrderId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Marketplace idempotency record points to a missing order"));
                    }
                }
            }

            insertOrder(connection, proposedOrder);
            return proposedOrder;
        });
    }

    private static void insertOrder(Connection connection, MarketplaceOrder order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_order
                    (order_id, tenant_id, buyer_id, currency, order_status,
                     cancellation_reason, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """)) {
            statement.setObject(1, order.id());
            statement.setString(2, order.tenantId());
            statement.setString(3, order.buyerId());
            statement.setString(4, order.currency());
            statement.setString(5, order.status().name());
            statement.setString(6, order.cancellationReason());
            statement.setTimestamp(7, Timestamp.from(order.createdAt()));
            statement.setTimestamp(8, Timestamp.from(order.updatedAt()));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_order_line
                    (tenant_id, order_id, line_index, line_id, product_id, offering_id,
                     seller_id, description, quantity, unit, unit_price, currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int i = 0; i < order.lines().size(); i++) {
                MarketplaceOrderLine line = order.lines().get(i);
                statement.setString(1, order.tenantId());
                statement.setObject(2, order.id());
                statement.setInt(3, i);
                statement.setString(4, line.lineId());
                statement.setString(5, line.productId());
                statement.setString(6, line.offeringId());
                statement.setString(7, line.sellerId());
                statement.setString(8, line.description());
                statement.setBigDecimal(9, line.quantity());
                statement.setString(10, line.unit());
                statement.setBigDecimal(11, line.unitPrice());
                statement.setString(12, line.currency());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Optional<MarketplaceOrder> loadOrder(Connection connection, String tenantId, UUID orderId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT order_id, buyer_id, currency, order_status, cancellation_reason, created_at, updated_at
                FROM marketplace_order WHERE tenant_id = ? AND order_id = ?
                """)) {
            statement.setString(1, tenantId);
            statement.setObject(2, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                UUID id = result.getObject("order_id", UUID.class);
                String buyerId = result.getString("buyer_id");
                String currency = result.getString("currency").trim();
                MarketplaceOrderStatus status = MarketplaceOrderStatus.valueOf(result.getString("order_status"));
                String cancellationReason = result.getString("cancellation_reason");
                var createdAt = result.getTimestamp("created_at").toInstant();
                var updatedAt = result.getTimestamp("updated_at").toInstant();
                List<MarketplaceOrderLine> lines = findLines(connection, tenantId, id);
                return Optional.of(MarketplaceOrder.reconstitute(id, tenantId, buyerId, lines,
                        currency, createdAt, updatedAt, status, cancellationReason));
            }
        }
    }

    @Override
    public Optional<MarketplaceOrder> findByTenantAndId(String tenantId, UUID orderId) {
        requireTenant(tenantId);
        Objects.requireNonNull(orderId, "orderId is required");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT order_id, tenant_id, buyer_id, currency, order_status,
                            cancellation_reason, created_at, updated_at
                     FROM marketplace_order WHERE tenant_id = ? AND order_id = ?
                     """)) {
            statement.setString(1, tenantId);
            statement.setObject(2, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                UUID id = result.getObject("order_id", UUID.class);
                String buyerId = result.getString("buyer_id");
                String currency = result.getString("currency").trim();
                MarketplaceOrderStatus status = MarketplaceOrderStatus.valueOf(result.getString("order_status"));
                String cancellationReason = result.getString("cancellation_reason");
                var createdAt = result.getTimestamp("created_at").toInstant();
                var updatedAt = result.getTimestamp("updated_at").toInstant();
                List<MarketplaceOrderLine> lines = findLines(connection, tenantId, id);
                return Optional.of(MarketplaceOrder.reconstitute(id, tenantId, buyerId, lines,
                        currency, createdAt, updatedAt, status, cancellationReason));
            }
        } catch (SQLException e) {
            throw persistenceFailure("Could not load marketplace order", e);
        }
    }

    @Override
    public boolean replaceIfCurrentStatus(String tenantId, UUID orderId,
                                          MarketplaceOrderStatus expectedStatus,
                                          MarketplaceOrder replacement) {
        requireTenant(tenantId);
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(expectedStatus, "expectedStatus is required");
        Objects.requireNonNull(replacement, "replacement is required");
        if (!orderId.equals(replacement.id()) || !tenantId.equals(replacement.tenantId())) {
            throw new IllegalArgumentException("Replacement order identity must match tenant and order id");
        }
        return inTransaction(connection -> {
            List<MarketplaceOrderLine> persistedLines = findLines(connection, tenantId, orderId);
            if (persistedLines.isEmpty() || !persistedLines.equals(replacement.lines())) {
                throw new IllegalArgumentException("Order identity and immutable line snapshots cannot be changed");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE marketplace_order
                    SET order_status = ?, cancellation_reason = ?, updated_at = ?, version = version + 1
                    WHERE tenant_id = ? AND order_id = ? AND order_status = ?
                      AND buyer_id = ? AND currency = ? AND created_at = ?
                    """)) {
                statement.setString(1, replacement.status().name());
                statement.setString(2, replacement.cancellationReason());
                statement.setTimestamp(3, Timestamp.from(replacement.updatedAt()));
                statement.setString(4, tenantId);
                statement.setObject(5, orderId);
                statement.setString(6, expectedStatus.name());
                statement.setString(7, replacement.buyerId());
                statement.setString(8, replacement.currency());
                statement.setTimestamp(9, Timestamp.from(replacement.createdAt()));
                return statement.executeUpdate() == 1;
            }
        });
    }

    private static List<MarketplaceOrderLine> findLines(Connection connection, String tenantId, UUID orderId)
            throws SQLException {
        List<MarketplaceOrderLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT line_id, product_id, offering_id, seller_id, description,
                       quantity, unit, unit_price, currency
                FROM marketplace_order_line
                WHERE tenant_id = ? AND order_id = ? ORDER BY line_index
                """)) {
            statement.setString(1, tenantId);
            statement.setObject(2, orderId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    lines.add(new MarketplaceOrderLine(
                            result.getString("line_id"), result.getString("product_id"),
                            result.getString("offering_id"), result.getString("seller_id"),
                            result.getString("description"), result.getBigDecimal("quantity"),
                            result.getString("unit"), result.getBigDecimal("unit_price"),
                            result.getString("currency").trim()));
                }
            }
        }
        return List.copyOf(lines);
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw persistenceFailure("Marketplace database operation failed", (SQLException) e);
            } finally {
                try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) { }
            }
        } catch (SQLException e) {
            throw persistenceFailure("Marketplace database connection failed", e);
        }
    }

    private static IllegalStateException persistenceFailure(String message, SQLException cause) {
        return new IllegalStateException(message, cause);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}