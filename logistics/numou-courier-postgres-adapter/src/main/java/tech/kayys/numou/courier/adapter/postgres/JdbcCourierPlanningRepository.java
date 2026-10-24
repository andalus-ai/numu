package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.domain.CourierEtaEstimate;
import tech.kayys.numou.courier.domain.CourierPlanningRepository;
import tech.kayys.numou.courier.domain.CourierRoutePlan;
import tech.kayys.numou.courier.domain.ShipmentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * PostgreSQL adapter for M8 planning snapshots. ETA history is append-only; route headers
 * and ordered stops are persisted in a single transaction to prevent partial plans.
 */
@ApplicationScoped
public class JdbcCourierPlanningRepository implements CourierPlanningRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcCourierPlanningRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletionStage<CourierEtaEstimate> saveEtaEstimate(TenantId tenantId, CourierEtaEstimate estimate) {
        if (tenantId == null || estimate == null) return failed(new IllegalArgumentException("tenantId and estimate are required"));
        if (!tenantId.equals(estimate.tenantId())) return failed(new SecurityException("ETA tenant mismatch"));
        String sql = """
            INSERT INTO courier_eta_estimates
              (tenant_id,id,shipment_id,estimated_earliest_at,estimated_latest_at,calculated_at,confidence,model_version,source,explanation_code)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, estimate.id());
            s.setObject(3, estimate.shipmentId().value());
            s.setTimestamp(4, Timestamp.from(estimate.estimatedEarliestAt()));
            s.setTimestamp(5, Timestamp.from(estimate.estimatedLatestAt()));
            s.setTimestamp(6, Timestamp.from(estimate.calculatedAt()));
            s.setBigDecimal(7, estimate.confidence());
            s.setString(8, estimate.modelVersion());
            s.setString(9, estimate.source().name());
            s.setString(10, estimate.explanationCode());
            if (s.executeUpdate() != 1) return failed(new IllegalStateException("ETA snapshot was not inserted"));
            return CompletableFuture.completedFuture(estimate);
        } catch (SQLException e) {
            return failed(new IllegalStateException("Unable to persist ETA snapshot", e));
        }
    }

    @Override
    public CompletionStage<Optional<CourierEtaEstimate>> findLatestEtaEstimate(TenantId tenantId, ShipmentId shipmentId) {
        if (tenantId == null || shipmentId == null) return failed(new IllegalArgumentException("tenantId and shipmentId are required"));
        String sql = """
            SELECT id,estimated_earliest_at,estimated_latest_at,calculated_at,confidence,model_version,source,explanation_code
            FROM courier_eta_estimates
            WHERE tenant_id=? AND shipment_id=?
            ORDER BY calculated_at DESC,id DESC
            LIMIT 1
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, shipmentId.value());
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return CompletableFuture.completedFuture(Optional.empty());
                CourierEtaEstimate estimate = new CourierEtaEstimate(
                    rs.getObject("id", UUID.class), tenantId, shipmentId,
                    instant(rs, "estimated_earliest_at"), instant(rs, "estimated_latest_at"),
                    instant(rs, "calculated_at"), rs.getBigDecimal("confidence"), rs.getString("model_version"),
                    CourierEtaEstimate.Source.valueOf(rs.getString("source")), rs.getString("explanation_code"));
                return CompletableFuture.completedFuture(Optional.of(estimate));
            }
        } catch (SQLException | RuntimeException e) {
            return failed(new IllegalStateException("Unable to read latest ETA snapshot", e));
        }
    }

    @Override
    public CompletionStage<CourierRoutePlan> saveRoutePlan(TenantId tenantId, CourierRoutePlan plan) {
        if (tenantId == null || plan == null) return failed(new IllegalArgumentException("tenantId and route plan are required"));
        if (!tenantId.equals(plan.tenantId())) return failed(new SecurityException("route-plan tenant mismatch"));
        String headerSql = """
            INSERT INTO courier_route_plans
              (tenant_id,id,branch_code,courier_reference,service_date,version,status,planned_at,planned_distance_meters,planned_duration_seconds)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;
        String stopSql = """
            INSERT INTO courier_route_plan_stops
              (tenant_id,route_plan_id,sequence,stop_reference,stop_type,planned_arrival_at)
            VALUES (?,?,?,?,?,?)
            """;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement(headerSql)) {
                    s.setObject(1, tenantId.value()); s.setObject(2, plan.id());
                    s.setString(3, plan.branchCode()); s.setString(4, plan.courierReference());
                    s.setObject(5, plan.serviceDate()); s.setInt(6, plan.version());
                    s.setString(7, plan.status().name()); s.setTimestamp(8, Timestamp.from(plan.plannedAt()));
                    if (plan.plannedDistanceMeters() == null) s.setNull(9, Types.BIGINT); else s.setLong(9, plan.plannedDistanceMeters());
                    if (plan.plannedDurationSeconds() == null) s.setNull(10, Types.INTEGER); else s.setInt(10, plan.plannedDurationSeconds());
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement(stopSql)) {
                    for (CourierRoutePlan.Stop stop : plan.stops()) {
                        s.setObject(1, tenantId.value()); s.setObject(2, plan.id()); s.setInt(3, stop.sequence());
                        s.setString(4, stop.stopReference()); s.setString(5, stop.type().name());
                        s.setTimestamp(6, Timestamp.from(stop.plannedArrivalAt()));
                        s.addBatch();
                    }
                    int[] counts = s.executeBatch();
                    for (int count : counts) {
                        if (count == Statement.EXECUTE_FAILED) throw new SQLException("route stop insert failed");
                    }
                }
                c.commit();
                return CompletableFuture.completedFuture(plan);
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignored) { /* connection closes immediately */ }
            }
        } catch (SQLException | RuntimeException e) {
            return failed(new IllegalStateException("Unable to persist route plan atomically", e));
        }
    }

    @Override
    public CompletionStage<Optional<CourierRoutePlan>> findRoutePlan(TenantId tenantId, UUID routePlanId) {
        if (tenantId == null || routePlanId == null) return failed(new IllegalArgumentException("tenantId and routePlanId are required"));
        String headerSql = """
            SELECT branch_code,courier_reference,service_date,version,status,planned_at,planned_distance_meters,planned_duration_seconds
            FROM courier_route_plans WHERE tenant_id=? AND id=?
            """;
        String stopSql = """
            SELECT sequence,stop_reference,stop_type,planned_arrival_at
            FROM courier_route_plan_stops WHERE tenant_id=? AND route_plan_id=? ORDER BY sequence
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(headerSql)) {
            s.setObject(1, tenantId.value()); s.setObject(2, routePlanId);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return CompletableFuture.completedFuture(Optional.empty());
                String branch = rs.getString("branch_code");
                String courier = rs.getString("courier_reference");
                LocalDate serviceDate = rs.getObject("service_date", LocalDate.class);
                int version = rs.getInt("version");
                CourierRoutePlan.Status status = CourierRoutePlan.Status.valueOf(rs.getString("status"));
                Instant plannedAt = instant(rs, "planned_at");
                long distanceValue = rs.getLong("planned_distance_meters");
                Long distance = rs.wasNull() ? null : distanceValue;
                int durationValue = rs.getInt("planned_duration_seconds");
                Integer duration = rs.wasNull() ? null : durationValue;
                List<CourierRoutePlan.Stop> stops = new ArrayList<>();
                try (PreparedStatement stop = c.prepareStatement(stopSql)) {
                    stop.setObject(1, tenantId.value()); stop.setObject(2, routePlanId);
                    try (ResultSet stopRs = stop.executeQuery()) {
                        while (stopRs.next()) {
                            stops.add(new CourierRoutePlan.Stop(stopRs.getInt("sequence"), stopRs.getString("stop_reference"),
                                CourierRoutePlan.Stop.StopType.valueOf(stopRs.getString("stop_type")), instant(stopRs, "planned_arrival_at")));
                        }
                    }
                }
                return CompletableFuture.completedFuture(Optional.of(new CourierRoutePlan(routePlanId, tenantId, branch, courier,
                    serviceDate, version, status, plannedAt, stops, distance, duration)));
            }
        } catch (SQLException | RuntimeException e) {
            return failed(new IllegalStateException("Unable to read route plan", e));
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
    private static <T> CompletionStage<T> failed(Throwable error) { return CompletableFuture.failedFuture(error); }
}
