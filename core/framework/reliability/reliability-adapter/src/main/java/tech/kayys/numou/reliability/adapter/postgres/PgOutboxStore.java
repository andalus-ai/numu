package tech.kayys.numou.reliability.adapter.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.reliability.adapter.json.JsonEventSerializer;
import tech.kayys.numou.reliability.outbox.OutboxClaim;
import tech.kayys.numou.reliability.outbox.OutboxEntry;
import tech.kayys.numou.reliability.spi.port.OutboxStorePort;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

/** PostgreSQL outbox with atomic lease acquisition and token-fenced state transitions. */
@ApplicationScoped
public class PgOutboxStore implements OutboxStorePort {
    static final String INSERT = """
            INSERT INTO platform_outbox (entry_id, event_id, tenant_id, event_type, payload, created_at, available_at)
            VALUES (?1, ?2, ?3, ?4, CAST(?5 AS jsonb), ?6, ?7)
            ON CONFLICT (event_id) DO NOTHING""";

    static final String CLAIM = """
            WITH candidates AS (
                SELECT entry_id
                  FROM platform_outbox
                 WHERE published_at IS NULL AND attempts < 10
                   AND available_at <= clock_timestamp()
                   AND (locked_until IS NULL OR locked_until <= clock_timestamp())
                 ORDER BY created_at, entry_id
                 LIMIT $1
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE platform_outbox o
               SET lease_token = gen_random_uuid(),
                   locked_until = clock_timestamp() + ($2::double precision * interval '1 millisecond')
              FROM candidates c
             WHERE o.entry_id = c.entry_id
            RETURNING o.entry_id, o.lease_token, o.locked_until, o.payload::text AS payload,
                      o.event_type, o.created_at, o.available_at, o.attempts, o.published_at""";

    static final String UPDATE = """
            UPDATE platform_outbox
               SET attempts = $3, available_at = $4,
                   published_at = $5, locked_until = NULL,
                   lease_token = NULL, last_error = $6
             WHERE entry_id = $1
               AND lease_token = $2
               AND locked_until > clock_timestamp()""";

    static final String FIND = """
            SELECT entry_id, payload::text AS payload, event_type, created_at, available_at, attempts, published_at
              FROM platform_outbox WHERE entry_id = $1""";
    static final String COUNT_PENDING = "SELECT count(*) AS n FROM platform_outbox WHERE published_at IS NULL AND attempts < 10";
    static final String COUNT_DEAD = "SELECT count(*) AS n FROM platform_outbox WHERE published_at IS NULL AND attempts >= 10";
    static final String OLDEST_PENDING_AGE = """
            SELECT coalesce(extract(epoch FROM (clock_timestamp() - min(created_at))), 0)::bigint AS age_s
              FROM platform_outbox WHERE published_at IS NULL AND attempts < 10""";

    @Inject PgPool pool;
    @Inject JsonEventSerializer serializer;

    @ConfigProperty(name = "numou.outbox.lease", defaultValue = "PT30S")
    Duration lease;

    @Override
    public Uni<Void> append(List<OutboxEntry> entries) {
        if (entries.isEmpty()) return Uni.createFrom().voidItem();
        return Panache.getSession().flatMap(session -> {
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (final OutboxEntry e : entries) {
                final var meta = e.envelope().metadata();
                chain = chain.flatMap(ignored -> session.createNativeQuery(INSERT)
                        .setParameter(1, e.entryId())
                        .setParameter(2, meta.eventId().value())
                        .setParameter(3, meta.tenantId())
                        .setParameter(4, e.envelope().eventType())
                        .setParameter(5, serializer.serialize(e.envelope()))
                        .setParameter(6, utc(e.createdAt()))
                        .setParameter(7, utc(e.availableAt()))
                        .executeUpdate().replaceWithVoid());
            }
            return chain;
        });
    }

    @Override
    public Uni<List<OutboxClaim>> nextDue(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        if (lease.isZero() || lease.isNegative()) throw new IllegalStateException("numou.outbox.lease must be positive");
        return pool.preparedQuery(CLAIM).execute(Tuple.of(limit, lease.toMillis()))
                .map(rows -> StreamSupport.stream(rows.spliterator(), false)
                        .map(this::toClaim)
                        .sorted(Comparator.comparing((OutboxClaim c) -> c.entry().createdAt())
                                .thenComparing(c -> c.entry().entryId()))
                        .toList());
    }

    @Override
    public Uni<Boolean> update(OutboxClaim claim, OutboxEntry entry) {
        return pool.preparedQuery(UPDATE)
                .execute(Tuple.of(claim.entry().entryId(), claim.token(), entry.attempts(),
                        utc(entry.availableAt()), entry.published() ? utc(Instant.now()) : null, null))
                .map(rows -> rows.rowCount() > 0);
    }

    @Override
    public Uni<OutboxEntry> findById(UUID entryId) {
        return pool.preparedQuery(FIND).execute(Tuple.of(entryId))
                .map(rows -> rows.iterator().hasNext() ? toEntry(rows.iterator().next()) : null);
    }

    @Override public Uni<Long> countPending() { return scalar(COUNT_PENDING, "n"); }
    public Uni<Long> countDeadLetters() { return scalar(COUNT_DEAD, "n"); }
    public Uni<Long> oldestPendingAgeSeconds() { return scalar(OLDEST_PENDING_AGE, "age_s"); }

    private Uni<Long> scalar(String sql, String column) {
        return pool.query(sql).execute().map(rows -> rows.iterator().next().getLong(column));
    }

    private OutboxClaim toClaim(Row row) {
        return new OutboxClaim(toEntry(row), row.getUUID("lease_token"),
                row.getOffsetDateTime("locked_until").toInstant());
    }

    private OutboxEntry toEntry(Row r) {
        final var envelope = serializer.deserialize(r.getString("payload"), r.getString("event_type"));
        final boolean published = r.getColumnIndex("published_at") >= 0 && r.getOffsetDateTime("published_at") != null;
        return new OutboxEntry(r.getUUID("entry_id"), envelope,
                r.getOffsetDateTime("created_at").toInstant(), r.getOffsetDateTime("available_at").toInstant(),
                r.getInteger("attempts"), published);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
