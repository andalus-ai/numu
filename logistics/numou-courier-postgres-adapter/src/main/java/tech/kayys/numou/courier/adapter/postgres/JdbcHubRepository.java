package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * PostgreSQL JDBC adapter. Every lookup and mutation predicates on tenant_id;
 * the unique (tenant_id, normalized_code) constraint is the final concurrency guard.
 */
@ApplicationScoped
public class JdbcHubRepository implements HubRepository {
    private static final String COLUMNS = "id, tenant_id, organization_id, branch_unit_id, code, normalized_code, "
            + "name, hub_type, status, address_line_1, address_line_2, locality, administrative_area, "
            + "postal_code, country_code, latitude, longitude, time_zone, created_at, updated_at";
    private final DataSource dataSource;

    @Inject
    public JdbcHubRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletionStage<Hub> save(TenantId tenantId, Hub hub) {
        if (tenantId == null || hub == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("tenantId and hub are required"));
        if (!tenantId.equals(hub.tenantId())) return CompletableFuture.failedFuture(
                new SecurityException("hub tenant does not match repository tenant"));
        String sql = "INSERT INTO courier_hubs (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (tenant_id, id) DO UPDATE SET organization_id=EXCLUDED.organization_id, "
                + "branch_unit_id=EXCLUDED.branch_unit_id, code=EXCLUDED.code, normalized_code=EXCLUDED.normalized_code, "
                + "name=EXCLUDED.name, hub_type=EXCLUDED.hub_type, status=EXCLUDED.status, "
                + "address_line_1=EXCLUDED.address_line_1, address_line_2=EXCLUDED.address_line_2, "
                + "locality=EXCLUDED.locality, administrative_area=EXCLUDED.administrative_area, "
                + "postal_code=EXCLUDED.postal_code, country_code=EXCLUDED.country_code, "
                + "latitude=EXCLUDED.latitude, longitude=EXCLUDED.longitude, time_zone=EXCLUDED.time_zone, "
                + "updated_at=EXCLUDED.updated_at WHERE courier_hubs.tenant_id = EXCLUDED.tenant_id";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            bindHub(s, tenantId, hub);
            s.executeUpdate();
            return CompletableFuture.completedFuture(hub);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState()) && e.getMessage() != null
                    && e.getMessage().contains("uq_courier_hubs_tenant_code")) {
                return CompletableFuture.failedFuture(new HubCodeConflictException(
                        "hub code already exists for this tenant", e));
            }
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to save hub", e));
        }
    }

    @Override
    public CompletionStage<Optional<Hub>> findById(TenantId tenantId, HubId hubId) {
        return findOne("SELECT " + COLUMNS + " FROM courier_hubs WHERE tenant_id = ? AND id = ?",
                tenantId, hubId == null ? null : hubId.value(), "load hub");
    }

    @Override
    public CompletionStage<Optional<Hub>> findByCode(TenantId tenantId, String code) {
        if (code == null) return CompletableFuture.failedFuture(new IllegalArgumentException("code is required"));
        return findOne("SELECT " + COLUMNS + " FROM courier_hubs WHERE tenant_id = ? AND normalized_code = ?",
                tenantId, code.trim().toUpperCase(Locale.ROOT), "load hub by code");
    }

    @Override
    public CompletionStage<Boolean> existsByCode(TenantId tenantId, String code) {
        if (tenantId == null || code == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("tenantId and code are required"));
        String sql = "SELECT EXISTS (SELECT 1 FROM courier_hubs WHERE tenant_id = ? AND normalized_code = ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setString(2, code.trim().toUpperCase(Locale.ROOT));
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return CompletableFuture.completedFuture(r.getBoolean(1));
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to check hub code", e));
        }
    }

    @Override
    public CompletionStage<Void> delete(TenantId tenantId, HubId hubId) {
        if (tenantId == null || hubId == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("tenantId and hubId are required"));
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("DELETE FROM courier_hubs WHERE tenant_id = ? AND id = ?")) {
            s.setObject(1, tenantId.value());
            s.setObject(2, hubId.value());
            s.executeUpdate();
            return CompletableFuture.completedFuture(null);
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to delete hub", e));
        }
    }

    private CompletionStage<Optional<Hub>> findOne(String sql, TenantId tenantId, Object value, String operation) {
        if (tenantId == null || value == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("tenantId and lookup value are required"));
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            if (value instanceof java.util.UUID uuid) s.setObject(2, uuid);
            else s.setString(2, value.toString());
            try (ResultSet r = s.executeQuery()) {
                return CompletableFuture.completedFuture(r.next()
                        ? Optional.of(mapHub(r)) : Optional.empty());
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to " + operation, e));
        }
    }

    private static Hub mapHub(ResultSet r) throws SQLException {
        Double latitude = nullableDouble(r, "latitude");
        Double longitude = nullableDouble(r, "longitude");
        HubAddress address = new HubAddress(r.getString("address_line_1"), r.getString("address_line_2"),
                r.getString("locality"), r.getString("administrative_area"), r.getString("postal_code"),
                r.getString("country_code"), latitude, longitude);
        return Hub.reconstitute(
                HubId.of(r.getObject("id", java.util.UUID.class)),
                TenantId.of(r.getObject("tenant_id", java.util.UUID.class)),
                OrganizationId.of(r.getObject("organization_id", java.util.UUID.class)),
                OrganizationUnitId.of(r.getObject("branch_unit_id", java.util.UUID.class)),
                r.getString("normalized_code"), r.getString("name"),
                HubType.valueOf(r.getString("hub_type")), address,
                ZoneId.of(r.getString("time_zone")), HubStatus.valueOf(r.getString("status")),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }

    private static Double nullableDouble(ResultSet r, String column) throws SQLException {
        double value = r.getDouble(column);
        return r.wasNull() ? null : value;
    }

    private static void bindHub(PreparedStatement s, TenantId tenantId, Hub hub) throws SQLException {
        int i = 1;
        s.setObject(i++, hub.id().value());
        s.setObject(i++, tenantId.value());
        s.setObject(i++, hub.organizationId().value());
        s.setObject(i++, hub.branchUnitId().value());
        s.setString(i++, hub.code());
        s.setString(i++, hub.code().trim().toUpperCase(Locale.ROOT));
        s.setString(i++, hub.name());
        s.setString(i++, hub.type().name());
        s.setString(i++, hub.status().name());
        HubAddress a = hub.address();
        s.setString(i++, a.addressLine1());
        s.setString(i++, a.addressLine2());
        s.setString(i++, a.locality());
        s.setString(i++, a.administrativeArea());
        s.setString(i++, a.postalCode());
        s.setString(i++, a.countryCode());
        if (a.latitude() == null) s.setNull(i++, Types.DECIMAL); else s.setDouble(i++, a.latitude());
        if (a.longitude() == null) s.setNull(i++, Types.DECIMAL); else s.setDouble(i++, a.longitude());
        s.setString(i++, hub.timeZone().getId());
        s.setTimestamp(i++, Timestamp.from(hub.createdAt()));
        s.setTimestamp(i, Timestamp.from(hub.updatedAt()));
    }

    /** Stable application-facing exception for a tenant-local duplicate hub code. */
    public static final class HubCodeConflictException extends IllegalStateException {
        public HubCodeConflictException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
