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
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Transactional scan receipt, custody transitions, tracking and outbox writer.
 * Aggregate rows are locked tenant-scoped and transitions commit atomically with scan facts.
 */
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
                       parcel_reference,scan_type,hub_reference,actor_reference,occurred_at,received_at,
                       container_id,dispatch_wave_id,seal_reference,manifest_reference,observed_seal_reference,discrepancy_reason)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                    s.setObject(13, command.containerId());
                    s.setObject(14, command.dispatchWaveId());
                    s.setString(15, command.sealReference());
                    s.setString(16, command.manifestReference());
                    s.setString(17, command.observedSealReference());
                    s.setString(18, command.discrepancyReason());
                    inserted = s.executeUpdate();
                }
                if (inserted == 0) {
                    ScanReceiptResult result = existingReceipt(c, command);
                    c.commit();
                    return CompletableFuture.completedFuture(result);
                }
                applyCustodyTransition(c, command);
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

    private static void applyCustodyTransition(Connection c, HubScanCommand cmd) throws SQLException {
        switch (cmd.type()) {
            case CONTAINER_SEALED -> sealContainer(c, cmd);
            case CONTAINER_DISPATCHED -> dispatchContainer(c, cmd);
            case CONTAINER_RECEIVED -> receiveContainer(c, cmd);
            case DISPATCH_LOADED -> recordWaveParcelScan(c, cmd);
            case LOAD_DISCREPANCY -> recordWaveDiscrepancy(c, cmd);
            case HUB_RECEIVED -> {
                // General shipment arrival does not transition container custody; use CONTAINER_RECEIVED.
            }
            case PICKUP_COLLECTED, HUB_INTAKE, SORTED, EXCEPTION_RECORDED -> {
                // No container/wave state transition implied by these scans.
            }
        }
    }

    private static void sealContainer(Connection c, HubScanCommand cmd) throws SQLException {
        ContainerRow row = lockContainer(c, cmd);
        if (!"OPEN".equals(row.status())) throw new IllegalStateException("only an open container can be sealed");
        if (!row.origin().equalsIgnoreCase(cmd.hubReference()))
            throw new IllegalStateException("container must be sealed at its origin hub");
        try (PreparedStatement s = c.prepareStatement(
                "SELECT count(*) FROM courier_transport_container_parcels WHERE tenant_id=? AND container_id=?")) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, cmd.containerId());
            try (ResultSet r = s.executeQuery()) {
                r.next();
                if (r.getLong(1) == 0) throw new IllegalStateException("empty container cannot be sealed");
            }
        }
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE courier_transport_containers
                SET status='SEALED', seal_reference=?, sealed_by=?, sealed_at=?, updated_at=GREATEST(updated_at,?)
                WHERE tenant_id=? AND id=? AND status='OPEN'
                """)) {
            s.setString(1, cmd.sealReference());
            s.setString(2, requiredActor(cmd));
            s.setTimestamp(3, Timestamp.from(cmd.occurredAt()));
            s.setTimestamp(4, Timestamp.from(cmd.occurredAt()));
            s.setObject(5, cmd.tenantId().value());
            s.setObject(6, cmd.containerId());
            requireOne(s.executeUpdate(), "container changed while sealing");
        }
    }

    private static void dispatchContainer(Connection c, HubScanCommand cmd) throws SQLException {
        ContainerRow container = lockContainer(c, cmd);
        if (!"SEALED".equals(container.status()))
            throw new IllegalStateException("only a sealed container can be dispatched");
        if (!container.origin().equalsIgnoreCase(cmd.hubReference()))
            throw new IllegalStateException("container must depart from its origin hub");
        WaveRow wave = lockWave(c, cmd);
        if (!"DEPARTED".equals(wave.status()))
            throw new IllegalStateException("container dispatch requires a departed dispatch wave");
        if (!container.origin().equalsIgnoreCase(wave.origin())
                || !container.destination().equalsIgnoreCase(wave.destination()))
            throw new IllegalStateException("container route does not match dispatch-wave route");
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE courier_transport_containers
                SET status='DISPATCHED', dispatched_with_manifest_reference=?, dispatched_at=?,
                    updated_at=GREATEST(updated_at,?)
                WHERE tenant_id=? AND id=? AND status='SEALED'
                """)) {
            s.setString(1, cmd.manifestReference());
            s.setTimestamp(2, Timestamp.from(cmd.occurredAt()));
            s.setTimestamp(3, Timestamp.from(cmd.occurredAt()));
            s.setObject(4, cmd.tenantId().value());
            s.setObject(5, cmd.containerId());
            requireOne(s.executeUpdate(), "container changed while dispatching");
        }
    }

    private static void receiveContainer(Connection c, HubScanCommand cmd) throws SQLException {
        ContainerRow row = lockContainer(c, cmd);
        if (!"DISPATCHED".equals(row.status()))
            throw new IllegalStateException("only a dispatched container can be received");
        if (!row.destination().equalsIgnoreCase(cmd.hubReference()))
            throw new IllegalStateException("container must be received at its destination hub");
        if (row.sealReference() == null || !row.sealReference().equals(cmd.observedSealReference()))
            throw new IllegalStateException("seal mismatch; route through the seal-exception workflow");
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE courier_transport_containers
                SET status='RECEIVED', received_by=?, received_at=?, updated_at=GREATEST(updated_at,?)
                WHERE tenant_id=? AND id=? AND status='DISPATCHED'
                """)) {
            s.setString(1, requiredActor(cmd));
            s.setTimestamp(2, Timestamp.from(cmd.occurredAt()));
            s.setTimestamp(3, Timestamp.from(cmd.occurredAt()));
            s.setObject(4, cmd.tenantId().value());
            s.setObject(5, cmd.containerId());
            requireOne(s.executeUpdate(), "container changed while receiving");
        }
    }

    private static void recordWaveParcelScan(Connection c, HubScanCommand cmd) throws SQLException {
        WaveRow wave = lockWave(c, cmd);
        if (!"LOADING".equals(wave.status()))
            throw new IllegalStateException("dispatch-loaded scan requires a wave in LOADING state");
        if (!wave.origin().equalsIgnoreCase(cmd.hubReference()))
            throw new IllegalStateException("scan hub does not match dispatch-wave origin");
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE courier_dispatch_wave_parcels
                SET scan_count=scan_count+1, first_scanned_at=COALESCE(first_scanned_at,?),
                    discrepancy=CASE WHEN expected THEN
                        CASE WHEN scan_count > 0 THEN 'DUPLICATE' ELSE discrepancy END
                        ELSE 'UNEXPECTED' END
                WHERE tenant_id=? AND wave_id=? AND parcel_reference=?
                RETURNING expected
                """)) {
            s.setTimestamp(1, Timestamp.from(cmd.occurredAt()));
            s.setObject(2, cmd.tenantId().value());
            s.setObject(3, cmd.dispatchWaveId());
            s.setString(4, cmd.parcelReference());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("parcel is not registered in dispatch wave");
                if (!r.getBoolean("expected")) throw new IllegalStateException("parcel is not expected in dispatch wave");
            }
        }
    }

    /**
     * Retains an observed loading discrepancy without emitting a successful-load tracking fact.
     * The wave lock serializes discrepancy classification with ordinary parcel-load scans.
     */
    private static void recordWaveDiscrepancy(Connection c, HubScanCommand cmd) throws SQLException {
        WaveRow wave = lockWave(c, cmd);
        if (!"LOADING".equals(wave.status()))
            throw new IllegalStateException("load discrepancy requires a wave in LOADING state");
        if (!wave.origin().equalsIgnoreCase(cmd.hubReference()))
            throw new IllegalStateException("discrepancy scan hub does not match wave origin");

        boolean expected = false;
        int scanCount = 0;
        try (PreparedStatement s = c.prepareStatement("""
                SELECT expected, scan_count FROM courier_dispatch_wave_parcels
                WHERE tenant_id=? AND wave_id=? AND parcel_reference=? FOR UPDATE
                """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, cmd.dispatchWaveId());
            s.setString(3, cmd.parcelReference());
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) {
                    expected = r.getBoolean("expected");
                    scanCount = r.getInt("scan_count");
                }
            }
        }

        String discrepancyType;
        if (!expected) {
            discrepancyType = "UNEXPECTED_PARCEL";
            try (PreparedStatement s = c.prepareStatement("""
                    INSERT INTO courier_dispatch_wave_parcels
                      (tenant_id,wave_id,parcel_reference,expected,first_scanned_at,scan_count,discrepancy)
                    VALUES (?,?,?,FALSE,?,1,'UNEXPECTED')
                    ON CONFLICT (tenant_id,wave_id,parcel_reference) DO UPDATE
                    SET scan_count=courier_dispatch_wave_parcels.scan_count+1,
                        first_scanned_at=COALESCE(courier_dispatch_wave_parcels.first_scanned_at,?),
                        discrepancy='UNEXPECTED'
                    """)) {
                s.setObject(1, cmd.tenantId().value());
                s.setObject(2, cmd.dispatchWaveId());
                s.setString(3, cmd.parcelReference());
                s.setTimestamp(4, Timestamp.from(cmd.occurredAt()));
                s.setTimestamp(5, Timestamp.from(cmd.occurredAt()));
                s.executeUpdate();
            }
        } else if (scanCount > 0) {
            discrepancyType = "DUPLICATE_SCAN";
            try (PreparedStatement s = c.prepareStatement("""
                    UPDATE courier_dispatch_wave_parcels
                    SET scan_count=scan_count+1, discrepancy='DUPLICATE'
                    WHERE tenant_id=? AND wave_id=? AND parcel_reference=?
                    """)) {
                s.setObject(1, cmd.tenantId().value());
                s.setObject(2, cmd.dispatchWaveId());
                s.setString(3, cmd.parcelReference());
                requireOne(s.executeUpdate(), "wave parcel changed while recording discrepancy");
            }
        } else {
            throw new IllegalStateException("expected parcel has not been scanned; use DISPATCH_LOADED");
        }

        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO courier_dispatch_wave_discrepancies
                  (tenant_id,id,wave_id,parcel_reference,discrepancy_type,reason,source,
                   idempotency_key,actor_reference,occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (tenant_id,source,idempotency_key) DO NOTHING
                """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, UUID.randomUUID());
            s.setObject(3, cmd.dispatchWaveId());
            s.setString(4, cmd.parcelReference());
            s.setString(5, discrepancyType);
            s.setString(6, cmd.discrepancyReason());
            s.setString(7, cmd.source());
            s.setString(8, cmd.idempotencyKey());
            s.setString(9, cmd.actorReference());
            s.setTimestamp(10, Timestamp.from(cmd.occurredAt()));
            s.executeUpdate();
        }
    }

    private static ContainerRow lockContainer(Connection c, HubScanCommand cmd) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                SELECT status,origin_hub_reference,destination_hub_reference,seal_reference
                FROM courier_transport_containers WHERE tenant_id=? AND id=? FOR UPDATE
                """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, cmd.containerId());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("container not found in command tenant");
                return new ContainerRow(r.getString("status"), r.getString("origin_hub_reference"),
                        r.getString("destination_hub_reference"), r.getString("seal_reference"));
            }
        }
    }

    private static WaveRow lockWave(Connection c, HubScanCommand cmd) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                SELECT status,origin_hub_reference,destination_hub_reference
                FROM courier_dispatch_waves WHERE tenant_id=? AND id=? FOR UPDATE
                """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, cmd.dispatchWaveId());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("dispatch wave not found in command tenant");
                return new WaveRow(r.getString("status"), r.getString("origin_hub_reference"),
                        r.getString("destination_hub_reference"));
            }
        }
    }

    private static String requiredActor(HubScanCommand cmd) {
        if (cmd.actorReference() == null || cmd.actorReference().isBlank())
            throw new IllegalStateException("custody transition requires an actor reference");
        return cmd.actorReference();
    }

    private static void requireOne(int count, String message) {
        if (count != 1) throw new IllegalStateException(message);
    }

    private record ContainerRow(String status, String origin, String destination, String sealReference) { }
    private record WaveRow(String status, String origin, String destination) { }

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
            case LOAD_DISCREPANCY -> "LOAD_DISCREPANCY";
            case CONTAINER_DISPATCHED -> "LINEHAUL_DEPARTED";
            case CONTAINER_RECEIVED -> "LINEHAUL_ARRIVED";
            case CONTAINER_SEALED -> "CONTAINER_SEALED";
            case EXCEPTION_RECORDED -> "EXCEPTION_RECORDED";
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
            + "\",\"containerId\":" + (cmd.containerId() == null ? "null" : "\"" + cmd.containerId() + "\"")
            + ",\"dispatchWaveId\":" + (cmd.dispatchWaveId() == null ? "null" : "\"" + cmd.dispatchWaveId() + "\"")
            + ",\"sealReference\":" + nullableJson(cmd.sealReference())
            + ",\"manifestReference\":" + nullableJson(cmd.manifestReference())
            + ",\"observedSealReference\":" + nullableJson(cmd.observedSealReference())
            + ",\"actorReference\":" + nullableJson(cmd.actorReference()) + "}";
        try (PreparedStatement s = c.prepareStatement("""
            INSERT INTO courier_operational_outbox
              (tenant_id,id,aggregate_type,aggregate_id,shipment_id,event_type,event_key,occurred_at,payload)
            VALUES (?,?,'HUB_SCAN',?,?,?,?,?,?::jsonb)
            ON CONFLICT (tenant_id,event_key) DO NOTHING
            """)) {
            s.setObject(1, cmd.tenantId().value());
            s.setObject(2, cmd.commandId());
            s.setObject(3, cmd.containerId() == null ? cmd.shipmentId().value() : cmd.containerId());
            s.setObject(4, cmd.shipmentId().value());
            s.setString(5, cmd.type().name());
            s.setString(6, "HUB_SCAN:" + cmd.source() + ":" + cmd.idempotencyKey());
            s.setTimestamp(7, Timestamp.from(cmd.occurredAt()));
            s.setString(8, payload);
            s.executeUpdate();
        }
    }

    private static String nullableJson(String value) {
        return value == null ? "null" : "\"" + json(value) + "\"";
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
