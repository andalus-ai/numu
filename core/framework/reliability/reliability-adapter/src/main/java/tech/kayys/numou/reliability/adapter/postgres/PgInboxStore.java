package tech.kayys.numou.reliability.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.reliability.inbox.InboxArrival;
import tech.kayys.numou.reliability.inbox.InboxEntry;
import tech.kayys.numou.reliability.spi.port.InboxStorePort;

import java.time.ZoneOffset;
import java.util.UUID;

/** Postgres inbox; first-writer-wins via {@code ON CONFLICT DO NOTHING} (verified against PG 16). NOT COMPILED in sandbox. */
@ApplicationScoped
public class PgInboxStore implements InboxStorePort {

    static final String INSERT = """
            INSERT INTO platform_inbox (entry_id, event_id, event_type, received_at) VALUES ($1, $2, $3, $4)
            ON CONFLICT (event_id) DO NOTHING RETURNING entry_id""";
    static final String BY_EVENT = "SELECT entry_id, event_id, event_type, received_at, handled FROM platform_inbox WHERE event_id = $1";
    static final String BY_ID = "SELECT entry_id, event_id, event_type, received_at, handled FROM platform_inbox WHERE entry_id = $1";
    static final String MARK = "UPDATE platform_inbox SET handled = true WHERE entry_id = $1";

    @Inject
    PgPool pool;

    @Override
    public Uni<InboxArrival> recordArrival(String eventId, String eventType) {
        final InboxEntry fresh = InboxEntry.firstArrival(eventId, eventType);
        return pool.preparedQuery(INSERT)
                .execute(Tuple.of(fresh.entryId(), eventId, eventType, fresh.receivedAt().atOffset(ZoneOffset.UTC)))
                .flatMap(rows -> rows.iterator().hasNext()
                        ? Uni.createFrom().item(new InboxArrival(fresh, true))
                        : pool.preparedQuery(BY_EVENT).execute(Tuple.of(eventId))
                                .map(r -> new InboxArrival(toEntry(r.iterator().next()), false)));
    }

    @Override
    public Uni<Boolean> alreadyHandled(String eventId) {
        return pool.preparedQuery(BY_EVENT).execute(Tuple.of(eventId))
                .map(r -> r.iterator().hasNext() && r.iterator().next().getBoolean("handled"));
    }

    @Override
    public Uni<Void> markHandled(InboxEntry entry) {
        return pool.preparedQuery(MARK).execute(Tuple.of(entry.entryId())).replaceWithVoid();
    }

    @Override
    public Uni<InboxEntry> findById(UUID entryId) {
        return pool.preparedQuery(BY_ID).execute(Tuple.of(entryId))
                .map(r -> r.iterator().hasNext() ? toEntry(r.iterator().next()) : null);
    }

    private InboxEntry toEntry(Row r) {
        return new InboxEntry(r.getUUID("entry_id"), r.getString("event_id"), r.getString("event_type"),
                r.getOffsetDateTime("received_at").toInstant(), r.getBoolean("handled"));
    }
}
