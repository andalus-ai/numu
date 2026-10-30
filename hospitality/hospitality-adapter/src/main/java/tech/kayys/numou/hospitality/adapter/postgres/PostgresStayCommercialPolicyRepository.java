package tech.kayys.numou.hospitality.adapter.postgres;

import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import tech.kayys.numou.hospitality.domain.NightlyRateOverride;
import tech.kayys.numou.hospitality.domain.StayDateRestriction;
import tech.kayys.numou.hospitality.domain.StayRatePlan;
import tech.kayys.numou.hospitality.spi.StayCommercialPolicyRepository;

/** PostgreSQL persistence for tenant-scoped Hospitality commercial policies. */
public final class PostgresStayCommercialPolicyRepository implements StayCommercialPolicyRepository {
    private final DataSource dataSource;
    private final Executor jdbcExecutor;

    public PostgresStayCommercialPolicyRepository(DataSource dataSource, Executor jdbcExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbcExecutor = Objects.requireNonNull(jdbcExecutor, "jdbcExecutor");
    }

    @Override
    public Uni<StayRatePlan> saveRatePlan(String tenantId, StayRatePlan plan) {
        requireTenant(tenantId);
        Objects.requireNonNull(plan, "plan");
        return async(() -> {
            String sql = "INSERT INTO hospitality_stay_rate_plan " +
                    "(tenant_id,rate_plan_id,listing_id,unit_id,code,name,currency,base_nightly_rate,valid_from,valid_until,enabled) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT (tenant_id,rate_plan_id) DO UPDATE SET code=EXCLUDED.code,name=EXCLUDED.name," +
                    "currency=EXCLUDED.currency,base_nightly_rate=EXCLUDED.base_nightly_rate," +
                    "valid_from=EXCLUDED.valid_from,valid_until=EXCLUDED.valid_until,enabled=EXCLUDED.enabled," +
                    "updated_at=CURRENT_TIMESTAMP WHERE hospitality_stay_rate_plan.listing_id=EXCLUDED.listing_id " +
                    "AND hospitality_stay_rate_plan.unit_id=EXCLUDED.unit_id";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId.trim());
                statement.setObject(2, plan.id());
                statement.setObject(3, plan.listingId());
                statement.setObject(4, plan.unitId());
                statement.setString(5, plan.code());
                statement.setString(6, plan.name());
                statement.setString(7, plan.currency());
                statement.setBigDecimal(8, plan.baseNightlyRate());
                statement.setDate(9, Date.valueOf(plan.validFrom()));
                statement.setDate(10, Date.valueOf(plan.validUntil()));
                statement.setBoolean(11, plan.enabled());
                if (statement.executeUpdate() != 1) {
                    throw new IllegalArgumentException("Rate plan identifier is already used in another scope");
                }
                return plan;
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<Optional<StayRatePlan>> findRatePlan(String tenantId, UUID listingId, UUID unitId, UUID ratePlanId) {
        requireScope(tenantId, listingId, unitId);
        Objects.requireNonNull(ratePlanId, "ratePlanId");
        return async(() -> {
            String sql = "SELECT * FROM hospitality_stay_rate_plan WHERE tenant_id=? AND listing_id=? AND unit_id=? AND rate_plan_id=?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bindScope(statement, tenantId, listingId, unitId);
                statement.setObject(4, ratePlanId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(readRatePlan(rs)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<List<StayRatePlan>> findRatePlans(String tenantId, UUID listingId, UUID unitId) {
        requireScope(tenantId, listingId, unitId);
        return async(() -> {
            String sql = "SELECT * FROM hospitality_stay_rate_plan WHERE tenant_id=? AND listing_id=? AND unit_id=? ORDER BY code,rate_plan_id";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bindScope(statement, tenantId, listingId, unitId);
                try (ResultSet rs = statement.executeQuery()) {
                    List<StayRatePlan> plans = new ArrayList<>();
                    while (rs.next()) plans.add(readRatePlan(rs));
                    return List.copyOf(plans);
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<Void> saveNightlyRateOverrides(String tenantId, UUID listingId, UUID unitId,
                                               UUID ratePlanId, List<NightlyRateOverride> overrides) {
        requireScope(tenantId, listingId, unitId);
        Objects.requireNonNull(ratePlanId, "ratePlanId");
        List<NightlyRateOverride> values = List.copyOf(Objects.requireNonNull(overrides, "overrides"));
        if (values.isEmpty()) return Uni.createFrom().voidItem();
        for (NightlyRateOverride value : values) {
            if (!ratePlanId.equals(value.ratePlanId())) {
                return Uni.createFrom().failure(new IllegalArgumentException("Override rate plan does not match request"));
            }
        }
        return async(() -> {
            String sql = "INSERT INTO hospitality_nightly_rate_override " +
                    "(tenant_id,listing_id,unit_id,rate_plan_id,stay_date,amount) VALUES (?,?,?,?,?,?) " +
                    "ON CONFLICT (tenant_id,rate_plan_id,stay_date) DO UPDATE SET amount=EXCLUDED.amount," +
                    "updated_at=CURRENT_TIMESTAMP WHERE hospitality_nightly_rate_override.listing_id=EXCLUDED.listing_id " +
                    "AND hospitality_nightly_rate_override.unit_id=EXCLUDED.unit_id";
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (NightlyRateOverride value : values) {
                        statement.setString(1, tenantId.trim());
                        statement.setObject(2, listingId);
                        statement.setObject(3, unitId);
                        statement.setObject(4, ratePlanId);
                        statement.setDate(5, Date.valueOf(value.date()));
                        statement.setBigDecimal(6, value.amount());
                        statement.addBatch();
                    }
                    int[] counts = statement.executeBatch();
                    for (int count : counts) {
                        if (count == 0 || count == java.sql.Statement.EXECUTE_FAILED) {
                            throw new IllegalArgumentException("Rate override scope conflicts with existing data");
                        }
                    }
                    connection.commit();
                    return null;
                } catch (RuntimeException | SQLException exception) {
                    rollback(connection, exception);
                    if (exception instanceof RuntimeException runtime) throw runtime;
                    throw persistenceFailure((SQLException) exception);
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<List<NightlyRateOverride>> findNightlyRateOverrides(String tenantId, UUID listingId, UUID unitId,
                                                                     UUID ratePlanId, java.time.LocalDate fromInclusive,
                                                                     java.time.LocalDate untilExclusive) {
        requireScope(tenantId, listingId, unitId);
        Objects.requireNonNull(ratePlanId, "ratePlanId");
        requireRange(fromInclusive, untilExclusive);
        return async(() -> {
            String sql = "SELECT rate_plan_id,stay_date,amount FROM hospitality_nightly_rate_override " +
                    "WHERE tenant_id=? AND listing_id=? AND unit_id=? AND rate_plan_id=? AND stay_date>=? AND stay_date<? " +
                    "ORDER BY stay_date";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bindScope(statement, tenantId, listingId, unitId);
                statement.setObject(4, ratePlanId);
                statement.setDate(5, Date.valueOf(fromInclusive));
                statement.setDate(6, Date.valueOf(untilExclusive));
                try (ResultSet rs = statement.executeQuery()) {
                    List<NightlyRateOverride> values = new ArrayList<>();
                    while (rs.next()) values.add(new NightlyRateOverride(rs.getObject("rate_plan_id", UUID.class),
                            rs.getDate("stay_date").toLocalDate(), rs.getBigDecimal("amount")));
                    return List.copyOf(values);
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<Void> saveStayDateRestrictions(String tenantId, UUID listingId, UUID unitId,
                                               List<StayDateRestriction> restrictions) {
        requireScope(tenantId, listingId, unitId);
        List<StayDateRestriction> values = List.copyOf(Objects.requireNonNull(restrictions, "restrictions"));
        if (values.isEmpty()) return Uni.createFrom().voidItem();
        for (StayDateRestriction value : values) {
            if (!listingId.equals(value.listingId()) || !unitId.equals(value.unitId())) {
                return Uni.createFrom().failure(new IllegalArgumentException("Restriction scope does not match request"));
            }
        }
        return async(() -> {
            String sql = "INSERT INTO hospitality_stay_date_restriction " +
                    "(tenant_id,listing_id,unit_id,stay_date,min_stay_nights,max_stay_nights," +
                    "closed_to_arrival,closed_to_departure,stop_sell) VALUES (?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT (tenant_id,listing_id,unit_id,stay_date) DO UPDATE SET " +
                    "min_stay_nights=EXCLUDED.min_stay_nights,max_stay_nights=EXCLUDED.max_stay_nights," +
                    "closed_to_arrival=EXCLUDED.closed_to_arrival,closed_to_departure=EXCLUDED.closed_to_departure," +
                    "stop_sell=EXCLUDED.stop_sell,updated_at=CURRENT_TIMESTAMP";
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (StayDateRestriction value : values) {
                        statement.setString(1, tenantId.trim());
                        statement.setObject(2, listingId);
                        statement.setObject(3, unitId);
                        statement.setDate(4, Date.valueOf(value.date()));
                        statement.setInt(5, value.minStayNights());
                        statement.setInt(6, value.maxStayNights());
                        statement.setBoolean(7, value.closedToArrival());
                        statement.setBoolean(8, value.closedToDeparture());
                        statement.setBoolean(9, value.stopSell());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                    connection.commit();
                    return null;
                } catch (RuntimeException | SQLException exception) {
                    rollback(connection, exception);
                    if (exception instanceof RuntimeException runtime) throw runtime;
                    throw persistenceFailure((SQLException) exception);
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<List<StayDateRestriction>> findStayDateRestrictions(String tenantId, UUID listingId, UUID unitId,
                                                                     java.time.LocalDate fromInclusive,
                                                                     java.time.LocalDate untilExclusive) {
        requireScope(tenantId, listingId, unitId);
        requireRange(fromInclusive, untilExclusive);
        return async(() -> {
            String sql = "SELECT * FROM hospitality_stay_date_restriction WHERE tenant_id=? AND listing_id=? " +
                    "AND unit_id=? AND stay_date>=? AND stay_date<? ORDER BY stay_date";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bindScope(statement, tenantId, listingId, unitId);
                statement.setDate(4, Date.valueOf(fromInclusive));
                statement.setDate(5, Date.valueOf(untilExclusive));
                try (ResultSet rs = statement.executeQuery()) {
                    List<StayDateRestriction> values = new ArrayList<>();
                    while (rs.next()) values.add(new StayDateRestriction(
                            rs.getObject("listing_id", UUID.class), rs.getObject("unit_id", UUID.class),
                            rs.getDate("stay_date").toLocalDate(), rs.getInt("min_stay_nights"),
                            rs.getInt("max_stay_nights"), rs.getBoolean("closed_to_arrival"),
                            rs.getBoolean("closed_to_departure"), rs.getBoolean("stop_sell")));
                    return List.copyOf(values);
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    private static StayRatePlan readRatePlan(ResultSet rs) throws SQLException {
        return new StayRatePlan(rs.getObject("rate_plan_id", UUID.class),
                rs.getObject("listing_id", UUID.class), rs.getObject("unit_id", UUID.class),
                rs.getString("code"), rs.getString("name"), rs.getString("currency").trim(),
                rs.getBigDecimal("base_nightly_rate"), rs.getDate("valid_from").toLocalDate(),
                rs.getDate("valid_until").toLocalDate(), rs.getBoolean("enabled"));
    }

    private static void bindScope(PreparedStatement statement, String tenantId,
                                  UUID listingId, UUID unitId) throws SQLException {
        statement.setString(1, tenantId.trim());
        statement.setObject(2, listingId);
        statement.setObject(3, unitId);
    }

    private static void requireScope(String tenantId, UUID listingId, UUID unitId) {
        requireTenant(tenantId);
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    }

    private static void requireRange(java.time.LocalDate fromInclusive, java.time.LocalDate untilExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(untilExclusive, "untilExclusive");
        if (!untilExclusive.isAfter(fromInclusive)) throw new IllegalArgumentException("untilExclusive must be after fromInclusive");
    }

    private static void rollback(Connection connection, Throwable failure) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }

    private static RuntimeException persistenceFailure(SQLException exception) {
        return new IllegalStateException("Hospitality commercial policy persistence operation failed", exception);
    }

    private <T> Uni<T> async(SqlSupplier<T> supplier) {
        CompletionStage<T> stage = CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException("Hospitality commercial policy operation failed", exception); }
        }, jdbcExecutor);
        return Uni.createFrom().completionStage(stage);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> { T get() throws Exception; }
}
