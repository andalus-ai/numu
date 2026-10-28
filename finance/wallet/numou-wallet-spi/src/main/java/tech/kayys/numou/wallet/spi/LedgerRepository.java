package tech.kayys.numou.wallet.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.wallet.domain.LedgerEntry;
import tech.kayys.numou.wallet.domain.WalletId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only history. Appending an entry whose id already exists is a silent no-op (replay-safe). */
public interface LedgerRepository {

    Uni<Void> append(UUID tenantId, LedgerEntry entry);

    /** Finds a durable operation by its idempotency reference, regardless of movement kind. */
    Uni<Optional<LedgerEntry>> findByRef(UUID tenantId, WalletId wallet, String ref);

    /** Entries of one wallet with {@code from <= at < to}, oldest first (statements). */
    Uni<List<LedgerEntry>> range(UUID tenantId, WalletId wallet, java.time.Instant from, java.time.Instant to, int limit);

    /** Newest first. */
    Uni<List<LedgerEntry>> history(UUID tenantId, WalletId wallet, int limit);
}
