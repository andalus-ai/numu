package tech.kayys.numou.reliability.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reliability.outbox.OutboxEntry;

import java.util.List;
import java.util.UUID;

/**
 * Persist and drain the transactional outbox (base01.md §P1-14).
 *
 * <p>{@code append} runs inside the owning business transaction - that is
 * the whole mechanism. {@code nextDue} is called by a relay loop, never by
 * business code.
 */
public interface OutboxStorePort {

    Uni<Void> append(List<OutboxEntry> entries);

    /** Returns entries whose available time has passed, oldest first. */
    Uni<List<OutboxEntry>> nextDue(int limit);

    Uni<Void> update(OutboxEntry entry);

    Uni<OutboxEntry> findById(UUID entryId);

    /** Number of entries still waiting - exported as a metric. */
    Uni<Long> countPending();
}
