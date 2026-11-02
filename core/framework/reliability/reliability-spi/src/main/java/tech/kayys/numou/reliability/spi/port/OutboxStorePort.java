package tech.kayys.numou.reliability.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reliability.outbox.OutboxClaim;
import tech.kayys.numou.reliability.outbox.OutboxEntry;
import java.util.List;
import java.util.UUID;

/** Transactional outbox persistence and leased relay claims. */
public interface OutboxStorePort {
    Uni<Void> append(List<OutboxEntry> entries);
    /** Atomically claims due rows and returns their ownership tokens. */
    Uni<List<OutboxClaim>> nextDue(int limit);
    /** Updates only if the supplied claim still owns a live lease. */
    Uni<Boolean> update(OutboxClaim claim, OutboxEntry entry);
    Uni<OutboxEntry> findById(UUID entryId);
    Uni<Long> countPending();
}
