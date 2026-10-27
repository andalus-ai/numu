package tech.kayys.numou.mobility.wallet.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.wallet.domain.LedgerEntry;
import tech.kayys.numou.mobility.wallet.domain.OwnerKind;
import tech.kayys.numou.mobility.wallet.domain.Wallet;
import tech.kayys.numou.mobility.wallet.domain.WalletId;
import tech.kayys.numou.mobility.wallet.spi.LedgerRepository;
import tech.kayys.numou.mobility.wallet.spi.WalletRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Wallet use cases. Every mutation is: load-or-open the wallet, apply an idempotent domain operation, save, append the
 * ledger line, publish events - in one unit of work, retried when it loses an optimistic-lock race. Retries are bounded
 * by {@code MAX_ATTEMPTS}, which must be at least the number of writers that can collide on one wallet; drivers and
 * riders have one or two, and the platform account is sharded precisely so no single row sees every ride.
 */
public final class WalletService {

    public static final int PLATFORM_SHARDS = 16;
    private static final int MAX_ATTEMPTS = 8;

    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final String currency;
    private final int fractionDigits;
    private final BigDecimal driverOverdraft;
    private final BigDecimal minPayout;

    public WalletService(WalletRepository wallets, LedgerRepository ledger, UnitOfWork uow, DomainEventSink events, DomainClock clock,
                         String currency, int fractionDigits, BigDecimal driverOverdraft, BigDecimal minPayout) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
        this.currency = currency;
        this.fractionDigits = fractionDigits;
        this.driverOverdraft = driverOverdraft;
        this.minPayout = minPayout;
    }

    @FunctionalInterface
    private interface Op {
        Wallet.Result apply(Wallet w, Instant now);
    }

    // ---------------------------------------------------------------- operations

    /** Adds money (payment-gateway top-up, driver earning, platform share). {@code ref} makes it idempotent. */
    public Uni<WalletView> credit(UUID tenantId, OwnerKind kind, UUID ownerId, BigDecimal amount, String ref, String memo) {
        return mutate(tenantId, kind, ownerId, LedgerEntry.Kind.CREDIT, ref, memo, amount, "credit", (w, now) -> w.credit(ref, amount, now));
    }

    /** Debits within the overdraft limit. */
    public Uni<WalletView> debit(UUID tenantId, OwnerKind kind, UUID ownerId, BigDecimal amount, String ref, String memo) {
        return mutate(tenantId, kind, ownerId, LedgerEntry.Kind.DEBIT, ref, memo, amount.negate(), "debit", (w, now) -> w.debit(ref, amount, now));
    }

    public Uni<WalletView> hold(UUID tenantId, OwnerKind kind, UUID ownerId, BigDecimal amount, String holdRef) {
        return mutate(tenantId, kind, ownerId, LedgerEntry.Kind.HOLD, holdRef, "hold", amount, "hold", (w, now) -> w.hold(holdRef, amount, now));
    }

    /** Charges {@code amount} against the hold {@code holdRef} (the debt-tolerant path: see {@link Wallet#capture}). */
    public Uni<WalletView> capture(UUID tenantId, OwnerKind kind, UUID ownerId, BigDecimal amount, String holdRef, String ref, String memo) {
        return mutate(tenantId, kind, ownerId, LedgerEntry.Kind.CAPTURE, ref, memo, amount.negate(), "capture|holdRef=" + holdRef, (w, now) -> w.capture(holdRef, ref, amount, now));
    }

    public Uni<WalletView> release(UUID tenantId, OwnerKind kind, UUID ownerId, String holdRef, String ref) {
        return mutate(tenantId, kind, ownerId, LedgerEntry.Kind.RELEASE, ref, "release", BigDecimal.ZERO, "release|holdRef=" + holdRef, (w, now) -> w.release(holdRef, ref, now));
    }

    /** Driver withdrawal to a bank account. The payout itself is executed by a separate disbursement service. */
    public Uni<WalletView> requestPayout(UUID tenantId, UUID driverId, BigDecimal amount, String ref) {
        if (amount.compareTo(minPayout) < 0) {
            return Uni.createFrom().failure(new BusinessRuleViolation("Minimum payout is " + minPayout.toPlainString()));
        }
        return mutate(tenantId, OwnerKind.DRIVER, driverId, LedgerEntry.Kind.DEBIT, ref, "payout", amount.negate(), "payout", (w, now) -> {
            if (w.balance().subtract(amount).signum() < 0) {
                throw new tech.kayys.numou.mobility.wallet.domain.InsufficientFundsException("Insufficient balance"); // payouts never use the overdraft
            }
            return w.debit(ref, amount, now);
        });
    }

    /** Platform revenue accrues to one of {@value #PLATFORM_SHARDS} shards chosen by the ride id, so no row is hot. */
    public Uni<WalletView> platformMove(UUID tenantId, UUID shardKey, BigDecimal signedAmount, String ref, String memo) {
        final UUID shard = platformShardOwner(tenantId, shardKey);
        return signedAmount.signum() >= 0
                ? credit(tenantId, OwnerKind.PLATFORM, shard, signedAmount, ref, memo)
                : mutate(tenantId, OwnerKind.PLATFORM, shard, LedgerEntry.Kind.DEBIT, ref, memo, signedAmount, "platformMove-debit",
                        (w, now) -> w.debit(ref, signedAmount.negate(), now));
    }

    // ---------------------------------------------------------------- reads

    public Uni<WalletView> view(UUID tenantId, OwnerKind kind, UUID ownerId) {
        return wallets.find(tenantId, WalletId.forOwner(kind, ownerId))
                .map(o -> o.map(WalletView::of).orElseGet(() -> WalletView.of(newWallet(kind, ownerId, clock.now()))));
    }

    public Uni<List<LedgerEntry>> history(UUID tenantId, OwnerKind kind, UUID ownerId, int limit) {
        return ledger.history(tenantId, WalletId.forOwner(kind, ownerId), Math.min(Math.max(limit, 1), 200));
    }

    /** The driver (or rider) statement for {@code from <= t < to}. */
    public Uni<EarningsStatement> statement(UUID tenantId, OwnerKind kind, UUID ownerId, Instant from, Instant to) {
        if (!to.isAfter(from) || java.time.Duration.between(from, to).toDays() > 366) {
            return Uni.createFrom().failure(new BusinessRuleViolation("Choose a period of at most one year"));
        }
        return ledger.range(tenantId, WalletId.forOwner(kind, ownerId), from, to, 5_000)
                .map(entries -> EarningsStatement.build(entries, from, to, fractionDigits));
    }

    /** Sum over all platform shards (reporting). */
    public Uni<BigDecimal> platformBalance(UUID tenantId) {
        Uni<BigDecimal> total = Uni.createFrom().item(BigDecimal.ZERO.setScale(fractionDigits));
        for (int i = 0; i < PLATFORM_SHARDS; i++) {
            final UUID owner = shardOwner(tenantId, i);
            total = total.flatMap(sum -> wallets.find(tenantId, WalletId.forOwner(OwnerKind.PLATFORM, owner))
                    .map(o -> o.map(w -> sum.add(w.balance())).orElse(sum)));
        }
        return total;
    }

    // ---------------------------------------------------------------- internals

    private Uni<WalletView> mutate(UUID tenantId, OwnerKind kind, UUID ownerId, LedgerEntry.Kind entryKind, String ref, String memo,
                                   BigDecimal signedAmount, String operationContext, Op op) {
        final var id = WalletId.forOwner(kind, ownerId);
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> ledger.findByRef(tenantId, id, ref).flatMap(existing -> {
            if (existing.isPresent()) {
                final LedgerEntry prior = existing.get();
                if (prior.kind() != entryKind || prior.amount().compareTo(signedAmount) != 0 || !prior.memo().equals(memo == null ? "" : memo)
                        || (!prior.requestFingerprint().isBlank() && !prior.requestFingerprint().equals(requestFingerprint(entryKind, signedAmount, memo, operationContext)))) {
                    return Uni.createFrom().failure(new BusinessRuleViolation("Idempotency reference was already used for a different wallet operation"));
                }
                return wallets.find(tenantId, id).map(o -> o.map(WalletView::of).orElseGet(() -> WalletView.of(newWallet(kind, ownerId, clock.now()))));
            }
            return wallets.find(tenantId, id)
                .map(o -> o.orElseGet(() -> newWallet(kind, ownerId, clock.now()))).flatMap(w -> {
                    final var now = clock.now();
                    final var result = op.apply(w, now);
                    if (!result.applied()) {
                        return Uni.createFrom().item(WalletView.of(w)); // replayed ref: nothing changed, nothing to save
                    }
                    return wallets.save(tenantId, w)
                            .flatMap(x -> ledger.append(tenantId, LedgerEntry.of(id, entryKind, signedAmount, w.balance(), ref, memo, now, w.storedVersion(), requestFingerprint(entryKind, signedAmount, memo, operationContext))))
                            .flatMap(x -> {
                                final var raised = w.pullDomainEvents();
                                return raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised);
                            })
                            .replaceWith(WalletView.of(w));
                })
        })));
    }

    private String requestFingerprint(LedgerEntry.Kind kind, BigDecimal signedAmount, String memo, String operationContext) {
        final String canonical = kind.name() + "\n" + signedAmount.stripTrailingZeros().toPlainString() + "\n"
                + (memo == null ? "" : memo) + "\n" + operationContext;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Wallet newWallet(OwnerKind kind, UUID ownerId, Instant now) {
        final BigDecimal overdraft = switch (kind) {
            case RIDER -> BigDecimal.ZERO.setScale(fractionDigits);
            case DRIVER -> driverOverdraft.setScale(fractionDigits);
            case PLATFORM -> null;
        };
        return Wallet.open(kind, ownerId, currency, fractionDigits, overdraft, now);
    }

    static UUID shardOwner(UUID tenantId, int shard) {
        return UUID.nameUUIDFromBytes(("platform:" + tenantId + ":" + shard).getBytes(StandardCharsets.UTF_8));
    }

    static UUID platformShardOwner(UUID tenantId, UUID key) {
        return shardOwner(tenantId, Math.floorMod(key.hashCode(), PLATFORM_SHARDS));
    }
}
