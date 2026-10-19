package tech.kayys.numou.construction.adapter.postgres.idempotency;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.construction.spi.idempotency.CommandIdempotencyStore;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.numou.construction.spi.outbox.TransactionalOutboxEvent;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * Transaction-bound PostgreSQL context. All idempotency statements use the
 * connection owned by PgTransactionalCommandRunner, never the pool.
 */
public final class PgTransactionalCommandContext implements TransactionalCommandContext {
    static final String INSERT_RESERVATION = """
            INSERT INTO construction_command_idempotency
                (tenant_id, idempotency_key, operation, request_fingerprint, state, created_at)
            VALUES ($1, $2, $3, $4, 'PROCESSING', $5)
            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
            """;

    static final String SELECT_RECORD = """
            SELECT operation, request_fingerprint, state, response_json
              FROM construction_command_idempotency
             WHERE tenant_id = $1 AND idempotency_key = $2
            """;

    static final String INSERT_OUTBOX_EVENT = """
            INSERT INTO construction_outbox_event
                (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload,
                 occurred_at, status, attempts, available_at)
            VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, 'PENDING', 0, $7)
            """;

    static final String COMPLETE = """
            UPDATE construction_command_idempotency
               SET state = 'COMPLETED', response_json = $4::jsonb, completed_at = $5
             WHERE tenant_id = $1 AND idempotency_key = $2
               AND request_fingerprint = $3 AND state = 'PROCESSING'
            """;

    private final SqlConnection connection;
    private final BoqRevisionOperations boqRevisionOperations;

    public PgTransactionalCommandContext(SqlConnection connection,
                                         BoqRevisionOperations boqRevisionOperations) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.boqRevisionOperations = Objects.requireNonNull(boqRevisionOperations, "boqRevisionOperations");
    }

    @Override
    public Uni<CommandIdempotencyStore.ReservationResult> reserve(
            CommandIdempotencyStore.Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        return connection.preparedQuery(INSERT_RESERVATION)
                .execute(Tuple.of(reservation.tenantId(), reservation.key(), reservation.operation(),
                        reservation.requestFingerprint(), toOffsetDateTime(reservation.createdAt())))
                .flatMap(inserted -> connection.preparedQuery(SELECT_RECORD)
                        .execute(Tuple.of(reservation.tenantId(), reservation.key()))
                        .map(rows -> reservationResult(inserted, rows, reservation)));
    }

    @Override
    public Uni<Boolean> complete(String tenantId, String key, String requestFingerprint,
                                 String responseJson, Instant completedAt) {
        requireText(tenantId, "tenantId");
        requireText(key, "key");
        requireText(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(responseJson, "responseJson");
        Objects.requireNonNull(completedAt, "completedAt");
        return connection.preparedQuery(COMPLETE)
                .execute(Tuple.of(tenantId, key, requestFingerprint, responseJson,
                        toOffsetDateTime(completedAt)))
                .map(updated -> updated.rowCount() == 1);
    }

    @Override
    public Uni<Optional<CommandIdempotencyStore.StoredOutcome>> find(String tenantId, String key) {
        requireText(tenantId, "tenantId");
        requireText(key, "key");
        return connection.preparedQuery(SELECT_RECORD).execute(Tuple.of(tenantId, key)).map(rows -> {
            if (!rows.iterator().hasNext()) return Optional.empty();
            return Optional.of(toOutcome(rows.iterator().next()));
        });
    }

    @Override
    public Uni<Void> appendOutboxEvent(TransactionalOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        return connection.preparedQuery(INSERT_OUTBOX_EVENT)
                .execute(Tuple.tuple()
                .addValue(event.eventId())
                .addValue(event.tenantId())
                .addValue(event.aggregateType())
                .addValue(event.aggregateId())
                .addValue(event.eventType())
                .addValue(event.payloadJson())
                .addValue(toOffsetDateTime(event.occurredAt())))
                .flatMap(rows -> rows.rowCount() == 1
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new IllegalStateException(
                                "Outbox event insert did not insert exactly one row")));
    }

    @Override
    public BoqRevisionOperations boqRevisions() {
        return boqRevisionOperations;
    }

    private static CommandIdempotencyStore.ReservationResult reservationResult(
            RowSet<Row> inserted, RowSet<Row> selected,
            CommandIdempotencyStore.Reservation requested) {
        if (!selected.iterator().hasNext()) {
            throw new IllegalStateException("Idempotency record missing after reservation attempt");
        }
        CommandIdempotencyStore.StoredOutcome outcome = toOutcome(selected.iterator().next());
        if (inserted.rowCount() == 1) {
            return new CommandIdempotencyStore.ReservationResult(
                    CommandIdempotencyStore.ReservationStatus.RESERVED, Optional.empty());
        }
        boolean sameRequest = requested.operation().equals(outcome.operation())
                && requested.requestFingerprint().equals(outcome.requestFingerprint());
        if (!sameRequest) {
            return new CommandIdempotencyStore.ReservationResult(
                    CommandIdempotencyStore.ReservationStatus.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                    Optional.of(outcome));
        }
        return new CommandIdempotencyStore.ReservationResult(
                CommandIdempotencyStore.ReservationStatus.EXISTING_MATCH, Optional.of(outcome));
    }

    private static CommandIdempotencyStore.StoredOutcome toOutcome(Row row) {
        return new CommandIdempotencyStore.StoredOutcome(
                row.getString("operation"), row.getString("request_fingerprint"),
                row.getString("state"), row.getString("response_json"));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
