package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL-backed at-least-once relay for reservation outbox events.
 *
 * <p>Claims use {@code FOR UPDATE SKIP LOCKED}, an expiring lease, and a unique token per
 * batch. A worker whose lease expires cannot acknowledge or reschedule a row reclaimed by
 * another worker. A crash after external publication but before acknowledgement may cause
 * redelivery; consumers must deduplicate by event ID.</p>
 *
 * <p>This class deliberately has no scheduler or broker dependency. The runtime owns polling,
 * lifecycle, metrics, and the concrete publisher.</p>
 */
public final class PgReservationOutboxRelay {
    private static final String DEAD_EXHAUSTED_SQL = """
            UPDATE reservation_outbox_event
               SET status = 'DEAD', lease_owner = NULL, lease_until = NULL,
                   last_error = COALESCE(last_error, 'Maximum delivery attempts exhausted')
             WHERE attempts >= $1
               AND ((status = 'PENDING' AND available_at <= clock_timestamp())
                 OR (status = 'PROCESSING' AND lease_until <= clock_timestamp()))
            """;

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT event_id
                  FROM reservation_outbox_event
                 WHERE attempts < $4
                   AND ((status = 'PENDING' AND available_at <= clock_timestamp())
                     OR (status = 'PROCESSING' AND lease_until <= clock_timestamp()))
                 ORDER BY available_at, occurred_at, event_id
                 LIMIT $1
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE reservation_outbox_event AS event
               SET status = 'PROCESSING',
                   attempts = event.attempts + 1,
                   lease_owner = $2,
                   lease_until = clock_timestamp() + make_interval(secs => $3),
                   last_error = NULL
              FROM candidates
             WHERE event.event_id = candidates.event_id
            RETURNING event.event_id, event.tenant_id, event.aggregate_type, event.aggregate_id,
                      event.event_type, event.payload::text AS payload, event.occurred_at,
                      event.attempts, event.lease_owner
            """;

    private static final String ACK_SQL = """
            UPDATE reservation_outbox_event
               SET status = 'PUBLISHED', published_at = clock_timestamp(),
                   lease_owner = NULL, lease_until = NULL, last_error = NULL
             WHERE event_id = $1 AND status = 'PROCESSING' AND lease_owner = $2
               AND lease_until > clock_timestamp()
            """;

    private static final String RETRY_SQL = """
            UPDATE reservation_outbox_event
               SET status = CASE WHEN attempts >= $4 THEN 'DEAD' ELSE 'PENDING' END,
                   available_at = CASE WHEN attempts >= $4 THEN available_at
                                       ELSE clock_timestamp() + make_interval(secs => $3) END,
                   lease_owner = NULL, lease_until = NULL, last_error = $5
             WHERE event_id = $1 AND status = 'PROCESSING' AND lease_owner = $2
            """;

    private final PgPool pool;
    private final ReservationOutboxPublisher publisher;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration leaseDuration;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public PgReservationOutboxRelay(PgPool pool, ReservationOutboxPublisher publisher,
                                    int batchSize, int maxAttempts, Duration leaseDuration,
                                    Duration initialBackoff, Duration maxBackoff) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.initialBackoff = positive(initialBackoff, "initialBackoff");
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        }
        if (leaseDuration.getSeconds() < 1 || leaseDuration.getSeconds() > Integer.MAX_VALUE
                || initialBackoff.getSeconds() < 1 || initialBackoff.getSeconds() > Integer.MAX_VALUE
                || maxBackoff.getSeconds() < 1 || maxBackoff.getSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Durations must be between one second and Integer.MAX_VALUE seconds");
        }
    }

    /**
     * Claims one batch, commits the lease before network I/O, then publishes sequentially.
     * The returned count includes only events whose publication and lease-guarded ACK both
     * succeeded. Failures are persisted for retry or dead-lettering and do not stop the batch.
     */
    public Uni<Integer> pollOnce() {
        String leaseOwner = UUID.randomUUID().toString();
        return pool.withTransaction(connection ->
                connection.preparedQuery(DEAD_EXHAUSTED_SQL).execute(Tuple.of(maxAttempts))
                        .chain(() -> connection.preparedQuery(CLAIM_SQL)
                                .execute(Tuple.of(batchSize, leaseOwner,
                                        (int) leaseDuration.getSeconds(), maxAttempts)))
                        .map(this::messages)
        ).chain(messages -> {
            Uni<Integer> delivered = Uni.createFrom().item(0);
            for (ReservationOutboxMessage message : messages) {
                delivered = delivered.chain(count -> deliver(message).map(ok -> count + (ok ? 1 : 0)));
            }
            return delivered;
        });
    }

    private Uni<Boolean> deliver(ReservationOutboxMessage message) {
        Uni<Void> publish;
        try {
            publish = publisher.publish(message);
            if (publish == null) {
                publish = Uni.createFrom().failure(
                        new IllegalStateException("Outbox publisher returned null Uni"));
            }
        } catch (Throwable failure) {
            publish = Uni.createFrom().failure(failure);
        }
        return publish
                .chain(() -> acknowledge(message))
                .onFailure().recoverWithUni(failure -> retry(message, failure).replaceWith(false));
    }

    private Uni<Boolean> acknowledge(ReservationOutboxMessage message) {
        return pool.preparedQuery(ACK_SQL)
                .execute(Tuple.of(message.eventId(), message.leaseOwner()))
                .map(rows -> rows.rowCount() == 1);
    }

    private Uni<Void> retry(ReservationOutboxMessage message, Throwable failure) {
        String reason = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        if (reason.length() > 4000) reason = reason.substring(0, 4000);
        int delay = backoffSeconds(message.attempts());
        return pool.preparedQuery(RETRY_SQL)
                .execute(Tuple.of(message.eventId(), message.leaseOwner(), delay, maxAttempts, reason))
                .replaceWithVoid();
    }

    private int backoffSeconds(int attempts) {
        long initial = initialBackoff.getSeconds();
        long cap = maxBackoff.getSeconds();
        long delay = initial;
        for (int i = 1; i < attempts && delay < cap; i++) {
            delay = Math.min(cap, delay > cap / 2 ? cap : delay * 2);
        }
        return (int) Math.min(delay, cap);
    }

    private List<ReservationOutboxMessage> messages(RowSet<Row> rows) {
        List<ReservationOutboxMessage> result = new ArrayList<>();
        for (Row row : rows) {
            result.add(new ReservationOutboxMessage(
                    row.getUUID("event_id"), row.getUUID("tenant_id"),
                    row.getString("aggregate_type"), row.getUUID("aggregate_id"),
                    row.getString("event_type"), row.getString("payload"),
                    row.getOffsetDateTime("occurred_at").toInstant(),
                    row.getInteger("attempts"), row.getString("lease_owner")));
        }
        return List.copyOf(result);
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
