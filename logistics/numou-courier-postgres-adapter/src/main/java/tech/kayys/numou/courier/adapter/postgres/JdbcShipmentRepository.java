package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.postgresql.util.PSQLException;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * JDBC shipment aggregate adapter. Shipment and all parcels are committed atomically.
 * Every read and mutation is scoped by tenant_id in SQL.
 */
@ApplicationScoped
public class JdbcShipmentRepository implements ShipmentRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcShipmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletionStage<Shipment> save(TenantId tenantId, Shipment shipment) {
        return saveInternal(tenantId, shipment, null, null);
    }

    @Override
    public CompletionStage<Shipment> saveIdempotently(TenantId tenantId, String idempotencyKey, String requestFingerprint, Shipment shipment) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200
                || requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("idempotency key and SHA-256 fingerprint are required; key length must be 1-200"));
        return saveInternal(tenantId, shipment, idempotencyKey.trim(), requestFingerprint);
    }

    private CompletionStage<Shipment> saveInternal(TenantId tenantId, Shipment shipment, String idempotencyKey, String requestFingerprint) {
        if (tenantId == null || shipment == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and shipment are required"));
        if (!tenantId.equals(shipment.tenantId()))
            return CompletableFuture.failedFuture(new SecurityException("shipment tenant does not match repository tenant"));
        if (shipment.status() == ShipmentStatus.DELIVERED) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "shipment delivery completion is managed by the proof-of-delivery projector"));
        }

        String sql = """
            INSERT INTO courier_shipments (
              id, tenant_id, reference, normalized_reference, status, service_level,
              sender_contact_name, sender_phone, sender_address_line_1, sender_address_line_2,
              sender_locality, sender_administrative_area, sender_postal_code, sender_country_code,
              recipient_contact_name, recipient_phone, recipient_address_line_1, recipient_address_line_2,
              recipient_locality, recipient_administrative_area, recipient_postal_code, recipient_country_code,
              created_at, updated_at, booked_at, delivered_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (tenant_id, id) DO UPDATE SET
              reference=EXCLUDED.reference, normalized_reference=EXCLUDED.normalized_reference,
              status=EXCLUDED.status, service_level=EXCLUDED.service_level,
              sender_contact_name=EXCLUDED.sender_contact_name, sender_phone=EXCLUDED.sender_phone,
              sender_address_line_1=EXCLUDED.sender_address_line_1, sender_address_line_2=EXCLUDED.sender_address_line_2,
              sender_locality=EXCLUDED.sender_locality, sender_administrative_area=EXCLUDED.sender_administrative_area,
              sender_postal_code=EXCLUDED.sender_postal_code, sender_country_code=EXCLUDED.sender_country_code,
              recipient_contact_name=EXCLUDED.recipient_contact_name, recipient_phone=EXCLUDED.recipient_phone,
              recipient_address_line_1=EXCLUDED.recipient_address_line_1, recipient_address_line_2=EXCLUDED.recipient_address_line_2,
              recipient_locality=EXCLUDED.recipient_locality, recipient_administrative_area=EXCLUDED.recipient_administrative_area,
              recipient_postal_code=EXCLUDED.recipient_postal_code, recipient_country_code=EXCLUDED.recipient_country_code,
              updated_at=EXCLUDED.updated_at, booked_at=EXCLUDED.booked_at, delivered_at=EXCLUDED.delivered_at
            WHERE courier_shipments.status = EXCLUDED.status
               OR (courier_shipments.status = 'DRAFT' AND EXCLUDED.status IN ('BOOKED', 'CANCELLED'))
               OR (courier_shipments.status = 'BOOKED' AND EXCLUDED.status = 'CANCELLED')
            """;
        try (Connection connection = dataSource.getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (idempotencyKey != null) {
                    int inserted;
                    try (PreparedStatement reserve = connection.prepareStatement("""
                            INSERT INTO courier_shipment_booking_keys
                              (tenant_id, idempotency_key, request_fingerprint, shipment_id, created_at)
                            VALUES (?, ?, ?, ?, now())
                            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                            """)) {
                        reserve.setObject(1, tenantId.value());
                        reserve.setString(2, idempotencyKey);
                        reserve.setString(3, requestFingerprint);
                        reserve.setObject(4, shipment.id().value());
                        inserted = reserve.executeUpdate();
                    }
                    if (inserted == 0) {
                        String storedFingerprint;
                        java.util.UUID storedShipmentId;
                        try (PreparedStatement lookup = connection.prepareStatement("""
                                SELECT request_fingerprint, shipment_id
                                FROM courier_shipment_booking_keys
                                WHERE tenant_id = ? AND idempotency_key = ?
                                FOR UPDATE
                                """)) {
                            lookup.setObject(1, tenantId.value());
                            lookup.setString(2, idempotencyKey);
                            try (ResultSet row = lookup.executeQuery()) {
                                if (!row.next()) throw new SQLException("idempotency reservation disappeared");
                                storedFingerprint = row.getString("request_fingerprint");
                                storedShipmentId = row.getObject("shipment_id", java.util.UUID.class);
                            }
                        }
                        connection.rollback();
                        if (!requestFingerprint.equals(storedFingerprint))
                            return CompletableFuture.failedFuture(new ShipmentBookingIdempotencyConflictException(
                                    "idempotency key was already used with a different booking payload"));
                        return findById(tenantId, ShipmentId.of(storedShipmentId)).thenApply(existing ->
                                existing.orElseThrow(() -> new IllegalStateException("idempotency record references a missing shipment")));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindShipment(statement, tenantId, shipment);
                    int affected = statement.executeUpdate();
                    if (affected == 0) {
                        throw new IllegalStateException("shipment lifecycle cannot transition from persisted state to "
                                + shipment.status());
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM courier_parcels WHERE tenant_id = ? AND shipment_id = ?")) {
                    delete.setObject(1, tenantId.value());
                    delete.setObject(2, shipment.id().value());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO courier_parcels
                          (tenant_id, id, shipment_id, reference, normalized_reference, weight_kg,
                           length_cm, width_cm, height_cm, handling_instructions, created_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    for (Parcel parcel : shipment.parcels()) {
                        insert.setObject(1, tenantId.value());
                        insert.setObject(2, parcel.id().value());
                        insert.setObject(3, shipment.id().value());
                        insert.setString(4, parcel.reference());
                        insert.setString(5, parcel.reference().trim().toUpperCase(Locale.ROOT));
                        insert.setBigDecimal(6, parcel.weightKg());
                        insert.setBigDecimal(7, parcel.lengthCm());
                        insert.setBigDecimal(8, parcel.widthCm());
                        insert.setBigDecimal(9, parcel.heightCm());
                        insert.setString(10, parcel.handlingInstructions());
                        insert.setTimestamp(11, Timestamp.from(shipment.createdAt()));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
                return CompletableFuture.completedFuture(shipment);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                if (failure instanceof SQLException sqlFailure && isReferenceConflict(sqlFailure)) {
                    return CompletableFuture.failedFuture(new ShipmentReferenceConflictException(
                            "shipment reference already exists for this tenant", sqlFailure));
                }
                return CompletableFuture.failedFuture(new IllegalStateException("Unable to save shipment", failure));
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to open shipment transaction", e));
        }
    }

    @Override
    public CompletionStage<Optional<Shipment>> findById(TenantId tenantId, ShipmentId shipmentId) {
        if (tenantId == null || shipmentId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and shipmentId are required"));
        return findOne(tenantId, "id = ?", shipmentId.value());
    }

    @Override
    public CompletionStage<Optional<Shipment>> findByReference(TenantId tenantId, String reference) {
        if (tenantId == null || reference == null || reference.isBlank())
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and reference are required"));
        return findOne(tenantId, "normalized_reference = ?", reference.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public CompletionStage<Void> deleteDraft(TenantId tenantId, ShipmentId shipmentId) {
        if (tenantId == null || shipmentId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and shipmentId are required"));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM courier_shipments WHERE tenant_id = ? AND id = ? AND status = 'DRAFT'")) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, shipmentId.value());
            statement.executeUpdate();
            return CompletableFuture.completedFuture(null);
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to delete draft shipment", e));
        }
    }

    private CompletionStage<Optional<Shipment>> findOne(TenantId tenantId, String predicate, Object value) {
        String sql = "SELECT * FROM courier_shipments WHERE tenant_id = ? AND " + predicate;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            if (value instanceof java.util.UUID uuid) statement.setObject(2, uuid);
            else statement.setString(2, value.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return CompletableFuture.completedFuture(Optional.empty());
                ShipmentId id = ShipmentId.of(row.getObject("id", java.util.UUID.class));
                List<Parcel> parcels = loadParcels(connection, tenantId, id);
                ShipmentAddress sender = address(row, "sender");
                ShipmentAddress recipient = address(row, "recipient");
                Shipment shipment = Shipment.reconstitute(id, tenantId, row.getString("reference"),
                        sender, recipient, ServiceLevel.valueOf(row.getString("service_level")),
                        ShipmentStatus.valueOf(row.getString("status")),
                        row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
                        row.getTimestamp("booked_at") == null ? null : row.getTimestamp("booked_at").toInstant(),
                        row.getTimestamp("delivered_at") == null ? null : row.getTimestamp("delivered_at").toInstant(),
                        parcels);
                return CompletableFuture.completedFuture(Optional.of(shipment));
            }
        } catch (SQLException | RuntimeException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to load shipment", e));
        }
    }

    private static List<Parcel> loadParcels(Connection connection, TenantId tenantId, ShipmentId shipmentId)
            throws SQLException {
        List<Parcel> parcels = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, reference, weight_kg, length_cm, width_cm, height_cm, handling_instructions
                FROM courier_parcels WHERE tenant_id = ? AND shipment_id = ? ORDER BY reference
                """)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, shipmentId.value());
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) parcels.add(new Parcel(
                        ParcelId.of(row.getObject("id", java.util.UUID.class)),
                        row.getString("reference"), row.getBigDecimal("weight_kg"),
                        row.getBigDecimal("length_cm"), row.getBigDecimal("width_cm"),
                        row.getBigDecimal("height_cm"), row.getString("handling_instructions")));
            }
        }
        return parcels;
    }

    private static ShipmentAddress address(ResultSet row, String prefix) throws SQLException {
        return new ShipmentAddress(row.getString(prefix + "_contact_name"), row.getString(prefix + "_phone"),
                row.getString(prefix + "_address_line_1"), row.getString(prefix + "_address_line_2"),
                row.getString(prefix + "_locality"), row.getString(prefix + "_administrative_area"),
                row.getString(prefix + "_postal_code"), row.getString(prefix + "_country_code"));
    }

    private static void bindShipment(PreparedStatement s, TenantId tenantId, Shipment shipment) throws SQLException {
        ShipmentAddress sender = shipment.sender();
        ShipmentAddress recipient = shipment.recipient();
        int i = 1;
        s.setObject(i++, shipment.id().value());
        s.setObject(i++, tenantId.value());
        s.setString(i++, shipment.reference());
        s.setString(i++, shipment.reference().trim().toUpperCase(Locale.ROOT));
        s.setString(i++, shipment.status().name());
        s.setString(i++, shipment.serviceLevel().name());
        i = bindAddress(s, i, sender);
        i = bindAddress(s, i, recipient);
        s.setTimestamp(i++, Timestamp.from(shipment.createdAt()));
        s.setTimestamp(i++, Timestamp.from(shipment.updatedAt()));
        if (shipment.bookedAt() == null) s.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE);
        else s.setTimestamp(i++, Timestamp.from(shipment.bookedAt()));
        if (shipment.deliveredAt() == null) s.setNull(i, Types.TIMESTAMP_WITH_TIMEZONE);
        else s.setTimestamp(i, Timestamp.from(shipment.deliveredAt()));
    }

    private static int bindAddress(PreparedStatement s, int i, ShipmentAddress a) throws SQLException {
        s.setString(i++, a.contactName());
        s.setString(i++, a.phone());
        s.setString(i++, a.addressLine1());
        s.setString(i++, a.addressLine2());
        s.setString(i++, a.locality());
        s.setString(i++, a.administrativeArea());
        s.setString(i++, a.postalCode());
        s.setString(i++, a.countryCode());
        return i;
    }

    private static boolean isReferenceConflict(SQLException e) {
        for (SQLException current = e; current != null; current = current.getNextException()) {
            if (!"23505".equals(current.getSQLState())) continue;
            if (current instanceof PSQLException postgres && postgres.getServerErrorMessage() != null
                    && "uq_courier_shipments_tenant_reference".equals(
                            postgres.getServerErrorMessage().getConstraint())) return true;
            if (current.getMessage() != null && current.getMessage().contains("uq_courier_shipments_tenant_reference"))
                return true;
        }
        return false;
    }

    public static final class ShipmentBookingIdempotencyConflictException extends RuntimeException {
        public ShipmentBookingIdempotencyConflictException(String message) { super(message); }
    }

    public static final class ShipmentReferenceConflictException extends RuntimeException {
        public ShipmentReferenceConflictException(String message, Throwable cause) { super(message, cause); }
    }
}
