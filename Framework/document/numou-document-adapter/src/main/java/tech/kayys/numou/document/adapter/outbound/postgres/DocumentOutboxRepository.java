package tech.kayys.syirkah.document.adapter.outbound.postgres;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentOutboxRepository {
    Uni<List<DocumentOutboxMessage>> claimPending(Instant now, Instant leaseUntil, int limit);

    Uni<Void> markPublished(UUID id, Instant publishedAt);

    Uni<Void> recordFailure(UUID id, String error, Instant nextAttemptAt, int maximumAttempts);
}
