package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.reservation.domain.FacilityId;
import tech.kayys.numou.reservation.domain.Reservation;
import tech.kayys.numou.reservation.domain.ReservationId;
import tech.kayys.numou.reservation.spi.IdempotentReservationRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Transaction-bound reservation repository. Never obtains its own pooled connection. */
public final class PgReservationRepository implements IdempotentReservationRepository {
    private final SqlConnection connection;

    public PgReservationRepository(SqlConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public Uni<Optional<Reservation>> find(UUID tenantId, ReservationId id) {
        requireTenant(tenantId);
        Objects.requireNonNull(id, "id");
        return connection.preparedQuery("""
                SELECT aggregate_version, snapshot FROM reservation_record
                 WHERE tenant_id = $1 AND reservation_id = $2
                """).execute(Tuple.of(tenantId, id.value()))
                .map(rows -> rows.iterator().hasNext()
                        ? Optional.of(restoreReservation(rows.iterator().next()))
                        : Optional.empty());
    }

    @Override
    public Uni<Void> save(UUID tenantId, Reservation reservation) {
        requireTenant(tenantId);
        Objects.requireNonNull(reservation, "reservation");
        long expected = reservation.storedVersion();
        long next = expected + 1;
        Reservation.Snapshot s = reservation.snapshot();
        String sql = expected == 0
                ? """
                  INSERT INTO reservation_record
                    (tenant_id, reservation_id, facility_id, starts_at, ends_at, status,
                     aggregate_version, snapshot, created_at, updated_at)
                  VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9, $10)
                  """
                : """
                  UPDATE reservation_record
                     SET facility_id = $3, starts_at = $4, ends_at = $5, status = $6,
                         aggregate_version = $7, snapshot = $8::jsonb, updated_at = $10
                   WHERE tenant_id = $1 AND reservation_id = $2 AND aggregate_version = $11
                  """;
        Tuple args = expected == 0
                ? Tuple.of(tenantId, s.id(), s.facilityId(), offset(s.startsAt()),
                    offset(s.startsAt().plusSeconds(s.durationSeconds())), s.status(), next,
                    json(s), offset(s.createdAt()), offset(s.updatedAt()))
                : Tuple.of(tenantId, s.id(), s.facilityId(), offset(s.startsAt()),
                    offset(s.startsAt().plusSeconds(s.durationSeconds())), s.status(), next,
                    json(s), offset(s.createdAt()), offset(s.updatedAt()), expected);
        return connection.preparedQuery(sql).execute(args).map(rows -> {
            if (rows.rowCount() != 1) {
                throw new OptimisticReservationWriteException("Reservation was concurrently modified: " + s.id());
            }
            reservation.markStored(next);
            return null;
        });
    }

    @Override
    public Uni<List<Reservation>> findStartingBetween(UUID tenantId, FacilityId facilityId,
                                                       Instant from, Instant to) {
        requireTenant(tenantId);
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!to.isAfter(from)) return Uni.createFrom().item(List.of());
        return connection.preparedQuery("""
                SELECT aggregate_version, snapshot FROM reservation_record
                 WHERE tenant_id = $1 AND facility_id = $2
                   AND starts_at >= $3 AND starts_at < $4
                 ORDER BY starts_at, reservation_id
                """).execute(Tuple.of(tenantId, facilityId.value(), offset(from), offset(to)))
                .map(rows -> {
                    List<Reservation> found = new ArrayList<>();
                    rows.forEach(row -> found.add(restoreReservation(row)));
                    return List.copyOf(found);
                });
    }

    @Override
    public Uni<Optional<Reservation>> findByIdempotencyKey(UUID tenantId, String key) {
        requireTenant(tenantId);
        requireKey(key);
        return connection.preparedQuery("""
                SELECT r.aggregate_version, r.snapshot
                  FROM reservation_idempotency_key i
                  JOIN reservation_record r
                    ON r.tenant_id = i.tenant_id AND r.reservation_id = i.reservation_id
                 WHERE i.tenant_id = $1 AND i.idempotency_key = $2
                """).execute(Tuple.of(tenantId, key))
                .map(rows -> rows.iterator().hasNext()
                        ? Optional.of(restoreReservation(rows.iterator().next()))
                        : Optional.empty());
    }

    @Override
    public Uni<IdempotentSaveResult> saveIdempotently(UUID tenantId, String key,
                                                       String fingerprint, Reservation candidate) {
        requireTenant(tenantId);
        requireKey(key);
        Objects.requireNonNull(candidate, "candidate");
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            return Uni.createFrom().failure(new IllegalArgumentException(
                    "requestFingerprint must be a lowercase SHA-256 hex digest"));
        }
        // The unique tenant/key constraint arbitrates concurrent requests. The FK is deferred,
        // so the key can be claimed before the reservation row is written in this transaction.
        return connection.preparedQuery("""
                INSERT INTO reservation_idempotency_key
                    (tenant_id, idempotency_key, request_fingerprint, reservation_id)
                VALUES ($1, $2, $3, $4)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """).execute(Tuple.of(tenantId, key, fingerprint, candidate.id().value()))
                .flatMap(insert -> {
                    if (insert.rowCount() == 1) {
                        return save(tenantId, candidate).replaceWith(new IdempotentSaveResult(candidate, true));
                    }
                    return connection.preparedQuery("""
                            SELECT request_fingerprint, reservation_id
                              FROM reservation_idempotency_key
                             WHERE tenant_id = $1 AND idempotency_key = $2
                             FOR UPDATE
                            """).execute(Tuple.of(tenantId, key)).flatMap(rows -> {
                        if (!rows.iterator().hasNext()) {
                            return Uni.createFrom().failure(new IllegalStateException(
                                    "Idempotency key disappeared during conflict resolution"));
                        }
                        Row row = rows.iterator().next();
                        if (!fingerprint.equals(row.getString("request_fingerprint").trim())) {
                            return Uni.createFrom().failure(new IdempotencyKeyConflictException(
                                    "Idempotency key was already used for a different reservation request"));
                        }
                        return find(tenantId, ReservationId.of(row.getUUID("reservation_id"))).map(found ->
                                new IdempotentSaveResult(found.orElseThrow(() -> new IllegalStateException(
                                        "Idempotency key references a missing reservation")), false));
                    });
                });
    }

    private static Reservation restoreReservation(Row row) {
        Reservation restored = Reservation.restore(asJsonObject(row.getValue("snapshot"))
                .mapTo(Reservation.Snapshot.class));
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
    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new IllegalArgumentException("idempotencyKey must contain 1-200 non-blank characters");
        }
    }

    public static final class OptimisticReservationWriteException extends RuntimeException {
        public OptimisticReservationWriteException(String message) { super(message); }
    }
}
