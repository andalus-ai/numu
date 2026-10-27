package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.reservation.domain.AvailabilityLedger;
import tech.kayys.numou.reservation.domain.FacilityId;
import tech.kayys.numou.reservation.domain.LedgerId;
import tech.kayys.numou.reservation.spi.LedgerRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction-bound availability ledger. A locked facility/day row serializes competing
 * allocation attempts; the caller must keep the surrounding transaction open through save.
 */
public final class PgLedgerRepository implements LedgerRepository {
    private final SqlConnection connection;

    public PgLedgerRepository(SqlConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public Uni<Optional<AvailabilityLedger>> find(UUID tenantId, LedgerId id) {
        requireTenant(tenantId);
        Objects.requireNonNull(id, "id");
        return connection.preparedQuery("""
                SELECT aggregate_version, snapshot FROM reservation_availability_ledger
                 WHERE tenant_id = $1 AND ledger_id = $2
                """).execute(Tuple.of(tenantId, id.value()))
                .map(rows -> rows.iterator().hasNext()
                        ? Optional.of(restoreLedger(rows.iterator().next()))
                        : Optional.empty());
    }

    @Override
    public Uni<Void> save(UUID tenantId, AvailabilityLedger ledger) {
        requireTenant(tenantId);
        Objects.requireNonNull(ledger, "ledger");
        long expected = ledger.storedVersion();
        long next = expected + 1;
        ledger.markStored(next);
        AvailabilityLedger.Snapshot s = ledger.snapshot();
        String sql = expected == 0
                ? """
                  INSERT INTO reservation_availability_ledger
                    (tenant_id, ledger_id, facility_id, ledger_day, aggregate_version, snapshot, created_at, updated_at)
                  VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $8)
                  ON CONFLICT (tenant_id, ledger_id) DO UPDATE
                     SET aggregate_version = EXCLUDED.aggregate_version,
                         snapshot = EXCLUDED.snapshot,
                         updated_at = EXCLUDED.updated_at
                   WHERE reservation_availability_ledger.aggregate_version = 0
                  """
                : """
                  UPDATE reservation_availability_ledger
                     SET aggregate_version = $5, snapshot = $6::jsonb, updated_at = $8
                   WHERE tenant_id = $1 AND ledger_id = $2 AND aggregate_version = $9
                  """;
        Tuple args = expected == 0
                ? Tuple.of(tenantId, s.ledgerId(), s.facilityId(), s.day(), next,
                    json(s), offset(s.createdAt()), offset(s.updatedAt()))
                : Tuple.of(tenantId, s.ledgerId(), s.facilityId(), s.day(), next,
                    json(s), offset(s.createdAt()), offset(s.updatedAt()), expected);
        return connection.preparedQuery(sql).execute(args).map(rows -> {
            if (rows.rowCount() != 1) {
                throw new PgReservationRepository.OptimisticReservationWriteException(
                        "Availability ledger was concurrently modified: " + s.ledgerId());
            }
            return null;
        });
    }

    @Override
    public Uni<AvailabilityLedger> findOrCreate(UUID tenantId, FacilityId facilityId, LocalDate day, Instant now) {
        requireTenant(tenantId);
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(now, "now");
        AvailabilityLedger.Snapshot initial = AvailabilityLedger.open(facilityId, day, now).snapshot();
        return connection.preparedQuery("""
                INSERT INTO reservation_availability_ledger
                    (tenant_id, ledger_id, facility_id, ledger_day, aggregate_version, snapshot, created_at, updated_at)
                VALUES ($1, $2, $3, $4, 0, $5::jsonb, $6, $6)
                ON CONFLICT (tenant_id, facility_id, ledger_day) DO NOTHING
                """).execute(Tuple.of(tenantId, initial.ledgerId(), initial.facilityId(), initial.day(),
                    json(initial), offset(now)))
                .flatMap(ignored -> connection.preparedQuery("""
                        SELECT aggregate_version, snapshot
                          FROM reservation_availability_ledger
                         WHERE tenant_id = $1 AND facility_id = $2 AND ledger_day = $3
                         FOR UPDATE
                        """).execute(Tuple.of(tenantId, facilityId.value(), day)))
                .map(rows -> {
                    if (!rows.iterator().hasNext()) {
                        throw new IllegalStateException("Availability ledger missing after insert-or-lock");
                    }
                    return restoreLedger(rows.iterator().next());
                });
    }

    private static AvailabilityLedger restoreLedger(Row row) {
        AvailabilityLedger restored = AvailabilityLedger.restore(asJsonObject(row.getValue("snapshot"))
                .mapTo(AvailabilityLedger.Snapshot.class));
        restored.markStored(row.getLong("aggregate_version"));
        return restored;
    }

    private static JsonObject asJsonObject(Object value) {
        if (value instanceof JsonObject object) return object;
        if (value instanceof String text) return new JsonObject(text);
        throw new IllegalStateException("Expected PostgreSQL JSONB snapshot but got " +
                (value == null ? "null" : value.getClass().getName()));
    }

    private static String json(Object snapshot) { return JsonObject.mapFrom(snapshot).encode(); }
    private static OffsetDateTime offset(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private static void requireTenant(UUID tenantId) { Objects.requireNonNull(tenantId, "tenantId"); }
}
