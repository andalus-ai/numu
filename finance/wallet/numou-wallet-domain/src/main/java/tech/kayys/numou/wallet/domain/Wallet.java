package tech.kayys.numou.wallet.domain;

import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.wallet.domain.event.WalletFrozen;
import tech.kayys.numou.wallet.domain.event.WalletOverdrawn;
import tech.kayys.numou.wallet.domain.event.WalletUnfrozen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A stored-value account.
 *
 * <ul>
 *   <li><b>Riders</b> top up, hold the estimated fare when booking, and the hold is captured at the end of the trip. If the
 *       final fare exceeds the hold (and the balance), the capture still succeeds and the balance goes negative: the rider
 *       owes the difference and {@link #canBook()} is false until they top up.</li>
 *   <li><b>Drivers</b> earn into the wallet; for cash rides the platform's share is debited, so the balance can go negative up
 *       to {@code overdraftLimit}. At the limit {@link #canAcceptCash()} turns false.</li>
 *   <li><b>Platform</b> accounts have an unlimited overdraft (promo subsidies exceed revenue on some rides).</li>
 * </ul>
 *
 * <p>Every mutating call carries a {@code ref} and is idempotent: replaying a ref returns {@code applied=false} and changes
 * nothing, which is what makes retried requests and redelivered events safe. The last {@value #MAX_REFS} refs are kept.
 */
public final class Wallet extends VersionedAggregate<WalletId> {

    private static final long serialVersionUID = 1L;
    static final int MAX_REFS = 500;

    public record Result(boolean applied, BigDecimal amount, BigDecimal shortfall) {
        static Result duplicate() { return new Result(false, BigDecimal.ZERO, BigDecimal.ZERO); }
    }

    public record Snapshot(UUID id, String ownerKind, UUID ownerId, String currency, int fractionDigits, BigDecimal balance,
                           Map<String, BigDecimal> holds, BigDecimal overdraftLimit, boolean frozen, List<String> refs,
                           Instant createdAt, Instant updatedAt, int version) { }

    private final OwnerKind ownerKind;
    private final UUID ownerId;
    private final String currency;
    private final int fractionDigits;
    private BigDecimal balance;
    private final Map<String, BigDecimal> holds = new LinkedHashMap<>();
    private final BigDecimal overdraftLimit;   // null = unlimited
    private boolean frozen;
    private final LinkedHashSet<String> refs = new LinkedHashSet<>();

    private Wallet(OwnerKind kind, UUID ownerId, String currency, int digits, BigDecimal overdraftLimit, Instant now) {
        super(WalletId.forOwner(kind, ownerId), now);
        this.ownerKind = Objects.requireNonNull(kind, "ownerKind cannot be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId cannot be null");
        this.currency = Objects.requireNonNull(currency, "currency cannot be null");
        this.fractionDigits = digits;
        this.overdraftLimit = overdraftLimit == null ? null : overdraftLimit.setScale(digits, RoundingMode.UNNECESSARY);
        this.balance = BigDecimal.ZERO.setScale(digits);
    }

    public static Wallet open(OwnerKind kind, UUID ownerId, String currency, int fractionDigits, BigDecimal overdraftLimit, Instant now) {
        if (overdraftLimit != null && overdraftLimit.signum() < 0) {
            throw new IllegalArgumentException("overdraftLimit cannot be negative");
        }
        return new Wallet(kind, ownerId, currency, fractionDigits, overdraftLimit, now);
    }

    // ---------------------------------------------------------------- money in / out

    public Result credit(String ref, BigDecimal amount, Instant now) {
        requirePositive(amount);
        if (seen(ref)) return Result.duplicate();
        balance = balance.add(scale(amount));
        remember(ref);
        touch(now);
        return new Result(true, scale(amount), zero());
    }

    /** Debits only if the result stays within the overdraft limit. Use {@link #capture} for money that is already owed. */
    public Result debit(String ref, BigDecimal amount, Instant now) {
        requirePositive(amount);
        requireActive();
        if (seen(ref)) return Result.duplicate();
        final BigDecimal after = available().subtract(scale(amount));
        if (overdraftLimit != null && after.add(overdraftLimit).signum() < 0) {
            throw new InsufficientFundsException("Insufficient balance");
        }
        balance = balance.subtract(scale(amount));
        remember(ref);
        touch(now);
        noteOverdraft(now);
        return new Result(true, scale(amount), zero());
    }

    /** Reserves {@code amount} of the available balance (never into overdraft). One hold per ref. */
    public Result hold(String ref, BigDecimal amount, Instant now) {
        requirePositive(amount);
        requireActive();
        if (seen(ref)) return Result.duplicate();
        if (available().compareTo(scale(amount)) < 0) {
            throw new InsufficientFundsException("Insufficient balance");
        }
        holds.put(ref, scale(amount));
        remember(ref);
        touch(now);
        return new Result(true, scale(amount), zero());
    }

    /**
     * Settles a charge. Consumes the hold stored under {@code holdRef} (if any) and debits {@code amount} from the balance.
     * Never refuses: the service has already been delivered. Any part that cannot be covered becomes the
     * {@code shortfall} and leaves the balance negative.
     */
    public Result capture(String holdRef, String ref, BigDecimal amount, Instant now) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        if (seen(ref)) return Result.duplicate();
        holds.remove(holdRef);
        balance = balance.subtract(scale(amount));
        final BigDecimal availableAfter = available();
        final BigDecimal shortfall = availableAfter.signum() < 0 ? availableAfter.negate().min(scale(amount)) : zero();
        remember(ref);
        touch(now);
        noteOverdraft(now);
        return new Result(true, scale(amount), shortfall);
    }

    /** Frees a hold without charging. A missing hold is not an error (the release may be replayed). */
    public Result release(String holdRef, String ref, Instant now) {
        if (seen(ref)) return Result.duplicate();
        final BigDecimal h = holds.remove(holdRef);
        remember(ref);
        touch(now);
        return new Result(true, h == null ? zero() : h, zero());
    }

    // ---------------------------------------------------------------- state

    public void freeze(String reason, Instant now) {
        if (frozen) return;
        frozen = true;
        touch(now);
        raise(WalletFrozen.at(now, id().value(), reason == null ? "" : reason));
    }

    public void unfreeze(Instant now) {
        if (!frozen) return;
        frozen = false;
        touch(now);
        raise(WalletUnfrozen.at(now, id().value()));
    }

    public BigDecimal available() {
        BigDecimal held = zero();
        for (final var h : holds.values()) {
            held = held.add(h);
        }
        return balance.subtract(held);
    }

    /** A rider may book only with no outstanding debt and an active wallet. */
    public boolean canBook() {
        return !frozen && available().signum() >= 0;
    }

    /** A driver may take a cash ride only while their commission debt is below the overdraft limit. */
    public boolean canAcceptCash() {
        return !frozen && (overdraftLimit == null || balance.add(overdraftLimit).signum() > 0);
    }

    public OwnerKind ownerKind() { return ownerKind; }
    public UUID ownerId() { return ownerId; }
    public String currency() { return currency; }
    public int fractionDigits() { return fractionDigits; }
    public BigDecimal balance() { return balance; }
    public boolean frozen() { return frozen; }
    public Map<String, BigDecimal> holds() { return Map.copyOf(holds); }
    public BigDecimal overdraftLimit() { return overdraftLimit; }

    // ---------------------------------------------------------------- internals

    private boolean seen(String ref) {
        return refs.contains(Objects.requireNonNull(ref, "ref cannot be null"));
    }

    private void remember(String ref) {
        refs.add(ref);
        while (refs.size() > MAX_REFS) {
            final var it = refs.iterator();
            it.next();
            it.remove();
        }
    }

    private void noteOverdraft(Instant now) {
        if (balance.signum() < 0) {
            raise(WalletOverdrawn.at(now, id().value(), ownerKind.name(), ownerId, balance));
        }
    }

    private void requireActive() {
        if (frozen) {
            throw new InvalidStateException("Wallet is frozen");
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleViolation("Amount must be positive");
        }
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(fractionDigits, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(fractionDigits);
    }

    // ---------------------------------------------------------------- persistence

    public Snapshot snapshot() {
        return new Snapshot(id().value(), ownerKind.name(), ownerId, currency, fractionDigits, balance, new LinkedHashMap<>(holds),
                overdraftLimit, frozen, new ArrayList<>(refs), createdAt, updatedAt, version);
    }

    public static Wallet restore(Snapshot s) {
        final var w = new Wallet(OwnerKind.valueOf(s.ownerKind()), s.ownerId(), s.currency(), s.fractionDigits(), s.overdraftLimit(), s.createdAt());
        w.balance = s.balance();
        w.holds.putAll(s.holds());
        w.frozen = s.frozen();
        w.refs.addAll(s.refs());
        w.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return w;
    }
}
