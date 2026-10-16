package tech.kayys.numou.construction.adapter.postgres.outbox;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.construction.spi.outbox.OutboxRelayStore;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * PostgreSQL implementation of the outbox relay store.
 *
 * <p>Claiming is a single UPDATE statement backed by a SKIP LOCKED CTE, so
 * competing workers cannot claim the same row in the same lease window. Broker
 * delivery must happen after this method completes, outside the DB transaction.
 * Acknowledge/failure updates are fenced by the random lease token.</p>
 *
 * <p>Requires the construction migrations in this bundle. This is a system
 * relay and must use a narrowly scoped service database role with only the
 * cross-tenant outbox permissions it needs; do not expose it to request paths.</p>
 */
@ApplicationScoped
public class PgOutboxRelayStore implements OutboxRelayStore {

    static final String CLAIM_BATCH = """
            WITH claimable AS (
                SELECT event_id
                  FROM construction_outbox_event
                 WHERE terminal = FALSE
                   AND available_at <= $1
                   AND (
                       status IN ('PENDING', 'FAILED')
                       OR (status = 'PUBLISHING' AND locked_until <= $1)
                   )
                 ORDER BY available_at, occurred_at, event_id
                 FOR UPDATE SKIP LOCKED
                 LIMIT $4
            )
            UPDATE construction_outbox_event AS event
               SET status = 'PUBLISHING',
                   worker_id = $2,
                   attempts = attempts + 1,
                   lease_token = gen_random_uuid(),
                   locked_until = $3
              FROM claimable
             WHERE event.event_id = claimable.event_id
            RETURNING event.event_id, event.aggregate_type, event.aggregate_id,
                      event.event_type, event.payload, event.occurred_at,
                      event.attempts, event.lease_token
            """;

    static final String MARK_PUBLISHED = """
            UPDATE construction_outbox_event
               SET status = 'PUBLISHED', published_at = $3,
                   worker_id = NULL, lease_token = NULL, locked_until = NULL,
                   last_error = NULL
             WHERE event_id = $1 AND status = 'PUBLISHING' AND lease_token = $2
            """;

    static final String MARK_FAILED = """
            UPDATE construction_outbox_event
               SET status = 'FAILED',
                   available_at = $3, last_error = $4, terminal = $5,
                   worker_id = NULL, lease_token = NULL, locked_until = NULL
             WHERE event_id = $1 AND status = 'PUBLISHING' AND lease_token = $2
            """;

    @Inject
    PgPool pool;

    @Override
    public Uni<List<ClaimedOutboxEvent>> claimBatch(
            String workerId, Instant now, Instant leaseUntil, int limit) {
        if (workerId == null || workerId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("workerId is required"));
        }
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(now)) {
            return Uni.createFrom().failure(new IllegalArgumentException("leaseUntil must be after now"));
        }
        if (limit < 1 || limit > 500) {
            return Uni.createFrom().failure(new IllegalArgumentException("limit must be between 1 and 500"));
        }

        return pool.preparedQuery(CLAIM_BATCH)
                .execute(Tuple.of(toOffsetDateTime(now), workerId, toOffsetDateTime(leaseUntil), limit))
                .map(rows -> StreamSupport.stream(rows.spliterator(), false)
                        .map(PgOutboxRelayStore::toClaimedEvent)
                        .toList());
    }

    @Override
    public Uni<Boolean> markPublished(UUID eventId, UUID leaseToken, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(publishedAt, "publishedAt");
        return pool.preparedQuery(MARK_PUBLISHED)
                .execute(Tuple.of(eventId, leaseToken, toOffsetDateTime(publishedAt)))
                .map(rows -> rows.rowCount() == 1);
    }

    @Override
    public Uni<Boolean> markFailed(
            UUID eventId, UUID leaseToken, Instant retryAt, String error, boolean terminal) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(retryAt, "retryAt");
        String safeError = error == null ? "Unknown delivery failure" : error;
        if (safeError.length() > 2000) safeError = safeError.substring(0, 2000);
        return pool.preparedQuery(MARK_FAILED)
                .execute(Tuple.of(eventId, leaseToken, toOffsetDateTime(retryAt), safeError, terminal))
                .map(rows -> rows.rowCount() == 1);
    }

    private static ClaimedOutboxEvent toClaimedEvent(Row row) {
        Object payloadValue = row.getValue("payload");
        String payload = payloadValue == null ? null : payloadValue.toString();
        return new ClaimedOutboxEvent(
                row.getUUID("event_id"),
                row.getString("aggregate_type"),
                row.getUUID("aggregate_id"),
                row.getString("event_type"),
                payload,
                row.getOffsetDateTime("occurred_at").toInstant(),
                row.getInteger("attempts"),
                row.getUUID("lease_token"));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
