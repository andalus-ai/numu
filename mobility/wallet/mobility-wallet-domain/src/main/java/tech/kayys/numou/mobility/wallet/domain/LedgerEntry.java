package tech.kayys.numou.mobility.wallet.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * One immutable line of a wallet's history. {@code amount} is signed (+ credit, - debit); a HOLD or RELEASE carries the
 * held amount and does not change the balance. {@code seq} is the wallet's own version at the time of the change: strictly increasing per
 * wallet and assigned atomically with the balance change, so it (not the timestamp, which can tie) defines the order of events. Entry ids are derived from (wallet, ref, kind), so re-appending the same
 * operation produces the same id and is recognisable as a duplicate.
 */
public record LedgerEntry(UUID id, UUID walletId, Kind kind, BigDecimal amount, BigDecimal balanceAfter, String ref, String memo, Instant at, long seq, String requestFingerprint) {
    /** Compatibility constructor for historical callers without a request fingerprint. */
    public LedgerEntry(UUID id, UUID walletId, Kind kind, BigDecimal amount, BigDecimal balanceAfter, String ref, String memo, Instant at, long seq) {
        this(id, walletId, kind, amount, balanceAfter, ref, memo, at, seq, "");
    }

    public enum Kind { CREDIT, DEBIT, HOLD, CAPTURE, RELEASE }

    public static LedgerEntry of(WalletId wallet, Kind kind, BigDecimal amount, BigDecimal balanceAfter, String ref, String memo, Instant at, long seq) {
        final var id = UUID.nameUUIDFromBytes(("ledger:" + wallet.value() + ":" + ref + ":" + kind).getBytes(StandardCharsets.UTF_8));
        return of(wallet, kind, amount, balanceAfter, ref, memo, at, seq, "");
    }

    public static LedgerEntry of(WalletId wallet, Kind kind, BigDecimal amount, BigDecimal balanceAfter, String ref, String memo, Instant at, long seq, String requestFingerprint) {
        final var id = UUID.nameUUIDFromBytes(("ledger:" + wallet.value() + ":" + ref + ":" + kind).getBytes(StandardCharsets.UTF_8));
        return new LedgerEntry(id, wallet.value(), kind, amount, balanceAfter, ref, memo == null ? "" : memo, at, seq, requestFingerprint == null ? "" : requestFingerprint);
    }
}
