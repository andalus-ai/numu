package tech.kayys.numou.construction.adapter.postgres.idempotency;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.construction.spi.idempotency.CommandIdempotencyStore;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL-backed idempotency record store.
 *
 * <p>Uses the unique (tenant_id, idempotency_key) primary key as the concurrency
 * arbiter. This class uses pool-level calls; it does NOT by itself make the
 * idempotency record and business writes one transaction. Production command
 * handlers must use a transaction-aware variant sharing the same SqlConnection
 * as their business writes before claiming atomic command execution.</p>
 */
@ApplicationScoped
public class PgCommandIdempotencyStore implements CommandIdempotencyStore {
    static final String INSERT_RESERVATION = """
            INSERT INTO construction_command_idempotency
                (tenant_id, idempotency_key, operation, request_fingerprint,
                 state, created_at)
            VALUES ($1, $2, $3, $4, 'PROCESSING', $5)
            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
            """;

    static final String SELECT_RECORD = """
            SELECT operation, request_fingerprint, state, response_json
              FROM construction_command_idempotency
             WHERE tenant_id = $1 AND idempotency_key = $2
            """;

    static final String COMPLETE = """
            UPDATE construction_command_idempotency
               SET state = 'COMPLETED', response_json = $4::jsonb, completed_at = $5
             WHERE tenant_id = $1 AND idempotency_key = $2
               AND request_fingerprint = $3 AND state = 'PROCESSING'
            """;

    @Inject
    PgPool pool;

    @Override
    public Uni<ReservationResult> reserve(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        return pool.preparedQuery(INSERT_RESERVATION)
                .execute(Tuple.of(reservation.tenantId(), reservation.key(), reservation.operation(),
                        reservation.requestFingerprint(), toOffsetDateTime(reservation.createdAt())))
                .flatMap(inserted -> pool.preparedQuery(SELECT_RECORD)
                        .execute(Tuple.of(reservation.tenantId(), reservation.key()))
                        .map(rows -> {
                            if (!rows.iterator().hasNext()) {
                                throw new IllegalStateException("Idempotency row disappeared after reservation");
                            }
                            Row row = rows.iterator().next();
                            StoredOutcome outcome = toOutcome(row);
                            if (inserted.rowCount() == 1) {
                                return new ReservationResult(ReservationStatus.RESERVED, Optional.empty());
                            }
                            boolean sameRequest = reservation.operation().equals(outcome.operation())
                                    && reservation.requestFingerprint().equals(outcome.requestFingerprint());
                            return sameRequest
                                    ? new ReservationResult(ReservationStatus.EXISTING_MATCH, Optional.of(outcome))
                                    : new ReservationResult(ReservationStatus.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                                            Optional.of(outcome));
                        }));
    }

    @Override
    public Uni<Boolean> complete(String tenantId, String key, String requestFingerprint,
                                  String responseJson, Instant completedAt) {
        requireText(tenantId, "tenantId");
        requireText(key, "key");
        requireText(requestFingerprint, "requestFingerprint");
        requireText(responseJson, "responseJson");
        Objects.requireNonNull(completedAt, "completedAt");
        return pool.preparedQuery(COMPLETE)
                .execute(Tuple.of(tenantId, key, requestFingerprint, responseJson,
                        toOffsetDateTime(completedAt)))
                .map(rows -> rows.rowCount() == 1);
    }

    @Override
    public Uni<Optional<StoredOutcome>> find(String tenantId, String key) {
        requireText(tenantId, "tenantId");
        requireText(key, "key");
        return pool.preparedQuery(SELECT_RECORD)
                .execute(Tuple.of(tenantId, key))
                .map(rows -> rows.iterator().hasNext()
                        ? Optional.of(toOutcome(rows.iterator().next()))
                        : Optional.empty());
    }

    private static StoredOutcome toOutcome(Row row) {
        Object response = row.getValue("response_json");
        return new StoredOutcome(row.getString("operation"), row.getString("request_fingerprint"),
                row.getString("state"), response == null ? null : response.toString());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
