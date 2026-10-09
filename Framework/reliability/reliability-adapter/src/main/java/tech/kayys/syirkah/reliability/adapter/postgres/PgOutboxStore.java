package tech.kayys.syirkah.reliability.adapter.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.syirkah.reliability.adapter.json.JsonEventSerializer;
import tech.kayys.syirkah.reliability.outbox.OutboxEntry;
import tech.kayys.syirkah.reliability.spi.port.OutboxStorePort;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * Postgres outbox. SQL is the exact text verified against PostgreSQL 16 (see docs/PRODUCTION.md).
 *
 * <p>{@link #append} runs on the Hibernate Reactive session so the insert joins the caller's
 * transaction (the whole point of an outbox). Everything the relay does is a separate,
 * short transaction on the Vert.x pool: claim (with a lease), publish, update.
 *
 * <p>NOT COMPILED in the authoring sandbox (no Maven Central): review against your Quarkus version.
 */
@ApplicationScoped
public class PgOutboxStore implements OutboxStorePort {

    static final String INSERT = """
            INSERT INTO platform_outbox (entry_id, event_id, tenant_id, event_type, payload, created_at, available_at)
            VALUES (?1, ?2, ?3, ?4, CAST(?5 AS jsonb), ?6, ?7)
            ON CONFLICT (event_id) DO NOTHING""";

    /** Claims due rows under a lease; SKIP LOCKED lets several relay replicas share the table. */
    static final String CLAIM = """
            UPDATE platform_outbox o SET locked_until = $2
            WHERE o.entry_id IN (
                SELECT entry_id FROM platform_outbox
                WHERE published_at IS NULL AND attempts < 10 AND available_at <= $1
                  AND (locked_until IS NULL OR locked_until <= $1)
                ORDER BY created_at, entry_id LIMIT $3 FOR UPDATE SKIP LOCKED)
            RETURNING o.entry_id, o.payload::text AS payload, o.event_type, o.created_at, o.available_at, o.attempts""";

    static final String UPDATE = """
            UPDATE platform_outbox
            SET attempts = $2, available_at = $3, published_at = $4, locked_until = NULL, last_error = $5
            WHERE entry_id = $1""";

    static final String FIND = """
            SELECT entry_id, payload::text AS payload, event_type, created_at, available_at, attempts, published_at
            FROM platform_outbox WHERE entry_id = $1""";

    static final String COUNT_PENDING = "SELECT count(*) AS n FROM platform_outbox WHERE published_at IS NULL AND attempts < 10";
    static final String COUNT_DEAD = "SELECT count(*) AS n FROM platform_outbox WHERE published_at IS NULL AND attempts >= 10";
    static final String OLDEST_PENDING_AGE = """
            SELECT coalesce(extract(epoch FROM (now() - min(created_at))), 0)::bigint AS age_s
            FROM platform_outbox WHERE published_at IS NULL AND attempts < 10""";

    @Inject
    PgPool pool;
    @Inject
    JsonEventSerializer serializer;

    @ConfigProperty(name = "syirkah.outbox.lease", defaultValue = "PT30S")
    Duration lease;

    @Override
    public Uni<Void> append(List<OutboxEntry> entries) {
        if (entries.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
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
    public Uni<List<OutboxEntry>> nextDue(int limit) {
        final Instant now = Instant.now();
        return pool.preparedQuery(CLAIM)
                .execute(Tuple.of(utc(now), utc(now.plus(lease)), limit))
                .map(rows -> StreamSupport.stream(rows.spliterator(), false)
                        .map(this::toEntry)
                        .sorted(Comparator.comparing(OutboxEntry::createdAt).thenComparing(OutboxEntry::entryId))
                        .toList());
    }

    @Override
    public Uni<Void> update(OutboxEntry e) {
        return pool.preparedQuery(UPDATE)
                .execute(Tuple.of(e.entryId(), e.attempts(), utc(e.availableAt()),
                        e.published() ? utc(Instant.now()) : null, null))
                .replaceWithVoid();
    }

    @Override
    public Uni<OutboxEntry> findById(UUID entryId) {
        return pool.preparedQuery(FIND).execute(Tuple.of(entryId))
                .map(rows -> rows.iterator().hasNext() ? toEntry(rows.iterator().next()) : null);
    }

    @Override
    public Uni<Long> countPending() {
        return scalar(COUNT_PENDING, "n");
    }

    /** Dead letters: expected to be zero; any value should page someone. */
    public Uni<Long> countDeadLetters() {
        return scalar(COUNT_DEAD, "n");
    }

    /** Seconds the oldest unpublished event has waited: the key outbox-lag SLI. */
    public Uni<Long> oldestPendingAgeSeconds() {
        return scalar(OLDEST_PENDING_AGE, "age_s");
    }

    private Uni<Long> scalar(String sql, String column) {
        return pool.query(sql).execute().map(rows -> rows.iterator().next().getLong(column));
    }

    private OutboxEntry toEntry(Row r) {
        final var envelope = serializer.deserialize(r.getString("payload"), r.getString("event_type"));
        final boolean published = hasColumn(r, "published_at") && r.getOffsetDateTime("published_at") != null;
        return new OutboxEntry(r.getUUID("entry_id"), envelope,
                r.getOffsetDateTime("created_at").toInstant(), r.getOffsetDateTime("available_at").toInstant(),
                r.getInteger("attempts"), published);
    }

    private static boolean hasColumn(Row r, String name) {
        return r.getColumnIndex(name) >= 0;
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
