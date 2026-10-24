package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.application.port.HubOperationsStore;
import tech.kayys.numou.courier.domain.HubScanCommand;
import tech.kayys.numou.courier.domain.HubScanType;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Transactional scan receipt, tracking and outbox writer. */
@ApplicationScoped
public class JdbcHubOperationsStore implements HubOperationsStore {
    private final DataSource dataSource;

    @Inject
    public JdbcHubOperationsStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public CompletionStage<ScanReceiptResult> ingest(HubScanCommand command) {
        if (command == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("command must not be null"));
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement s = c.prepareStatement("""
                    INSERT INTO courier_hub_scan_receipts
                      (tenant_id,id,source,idempotency_key,payload_fingerprint,shipment_id,
                       parcel_reference,scan_type,hub_reference,actor_reference,occurred_at,received_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (tenant_id,source,idempotency_key) DO NOTHING
                    """)) {
                    s.setObject(1, command.tenantId().value());
                    s.setObject(2, command.commandId());
                    s.setString(3, command.source());
                    s.setString(4, command.idempotencyKey());
                    s.setString(5, command.payloadFingerprint());
                    s.setObject(6, command.shipmentId().value());
                    s.setString(7, command.parcelReference());
                    s.setString(8, command.type().name());
                    s.setString(9, command.hubReference());
                    s.setString(10, command.actorReference());
                    s.setTimestamp(11, Timestamp.from(command.occurredAt()));
                    s.setTimestamp(12, Timestamp.from(command.receivedAt()));
                    inserted = s.executeUpdate();
                }
                if (inserted == 0) {
                    ScanReceiptResult result = existingReceipt(c, command);
                    c.commit();
                    return CompletableFuture.completedFuture(result);
                }
                appendTracking(c, command);
                appendOutbox(c, command);
                c.commit();
                return CompletableFuture.completedFuture(
                        new ScanReceiptResult(Outcome.ACCEPTED, command.commandId().toString()));
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Unable to ingest hub scan transaction", e));
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to open hub scan transaction", e));
        }
    }

    private static ScanReceiptResult existingReceipt(Connection c, HubScanCommand cmd) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
            SELECT id,payload_fingerprint FROM courier_hub_scan_receipts
            WHERE tenant_id=? AND source=? AND idempotency_key=? FOR SHARE
            """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setString(2, cmd.source());
            s.setString(3, cmd.idempotencyKey());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new SQLException("idempotency conflict row disappeared");
                String id = r.getObject("id").toString();
                return new ScanReceiptResult(
                    cmd.payloadFingerprint().equals(r.getString("payload_fingerprint"))
                        ? Outcome.REPLAYED : Outcome.IDEMPOTENCY_CONFLICT, id);
            }
        }
    }

    private static void appendTracking(Connection c, HubScanCommand cmd) throws SQLException {
        String type = switch (cmd.type()) {
            case PICKUP_COLLECTED -> "PICKED_UP";
            case HUB_INTAKE, HUB_RECEIVED -> "ARRIVED_AT_HUB";
            case SORTED -> "SORTED_AT_HUB";
            case DISPATCH_LOADED -> "LOADED_FOR_LINEHAUL";
            case CONTAINER_DISPATCHED -> "LINEHAUL_DEPARTED";
            case CONTAINER_RECEIVED -> "LINEHAUL_ARRIVED";
            case CONTAINER_SEALED, EXCEPTION_RECORDED -> "EXCEPTION_RECORDED";
        };
        try (PreparedStatement s = c.prepareStatement("""
            INSERT INTO courier_tracking_events
              (tenant_id,id,shipment_id,parcel_id,event_type,occurred_at,recorded_at,
               location_code,actor_reference,source,idempotency_key,note)
            VALUES (?,?,?,NULL,?,?,GREATEST(now(),?),?,?,?,?,?)
            ON CONFLICT (tenant_id,idempotency_key) DO NOTHING
            """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, cmd.commandId());
            s.setObject(3, cmd.shipmentId().value());
            s.setString(4, type);
            s.setTimestamp(5, Timestamp.from(cmd.occurredAt()));
            s.setTimestamp(6, Timestamp.from(cmd.occurredAt()));
            s.setString(7, cmd.hubReference());
            s.setString(8, cmd.actorReference());
            s.setString(9, "HUB_SCAN");
            s.setString(10, "HUB_SCAN:" + digest(cmd.source() + "\n" + cmd.idempotencyKey()));
            s.setString(11, "Hub scan " + cmd.type().name() + " for parcel " + cmd.parcelReference());
            s.executeUpdate();
        }
    }

    private static void appendOutbox(Connection c, HubScanCommand cmd) throws SQLException {
        String payload = "{\"scanType\":\"" + json(cmd.type().name())
            + "\",\"parcelReference\":\"" + json(cmd.parcelReference())
            + "\",\"hubReference\":\"" + json(cmd.hubReference())
            + "\",\"actorReference\":" + (cmd.actorReference() == null ? "null"
                : "\"" + json(cmd.actorReference()) + "\"") + "}";
        try (PreparedStatement s = c.prepareStatement("""
            INSERT INTO courier_operational_outbox
              (tenant_id,id,aggregate_type,aggregate_id,shipment_id,event_type,event_key,occurred_at,payload)
            VALUES (?,?,'HUB_SCAN',?,?,?,?,?,?::jsonb)
            ON CONFLICT (tenant_id,event_key) DO NOTHING
            """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, cmd.commandId());
            s.setObject(3, cmd.shipmentId().value());
            s.setObject(4, cmd.shipmentId().value());
            s.setString(5, cmd.type().name());
            s.setString(6, "HUB_SCAN:" + cmd.source() + ":" + cmd.idempotencyKey());
            s.setTimestamp(7, Timestamp.from(cmd.occurredAt()));
            s.setString(8, payload);
            s.executeUpdate();
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String json(String value) {
        StringBuilder b = new StringBuilder();
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch)); else b.append(ch); }
            }
        }
        return b.toString();
    }
}
