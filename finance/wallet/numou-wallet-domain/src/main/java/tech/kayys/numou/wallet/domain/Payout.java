package tech.kayys.numou.wallet.domain;

import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.wallet.domain.event.PayoutFailed;
import tech.kayys.numou.wallet.domain.event.PayoutPaid;
import tech.kayys.numou.wallet.domain.event.PayoutRequested;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A driver's withdrawal to a bank account. The wallet is debited when the payout is <em>requested</em> (so the same money cannot be withdrawn
 * twice); if the bank transfer fails, the debit is reversed exactly once. {@code bankAccountRef} is an opaque token from the payment provider's
 * vault: this system never stores a bank account number.
 */
public final class Payout extends VersionedAggregate<PayoutId> {

    private static final long serialVersionUID = 1L;

    public enum Status { REQUESTED, SUBMITTED, PAID, FAILED }

    public record Snapshot(UUID id, UUID driverId, BigDecimal amount, String currency, String bankAccountRef, String status, String providerRef,
                           String failureReason, Instant createdAt, Instant updatedAt, int version) { }

    private final UUID driverId;
    private final BigDecimal amount;
    private final String currency;
    private final String bankAccountRef;
    private Status status = Status.REQUESTED;
    private String providerRef;
    private String failureReason;

    private Payout(PayoutId id, UUID driverId, BigDecimal amount, String currency, String bankAccountRef, Instant now) {
        super(id, now);
        this.driverId = driverId;
        this.amount = amount;
        this.currency = currency;
        this.bankAccountRef = bankAccountRef;
    }

    public static Payout request(PayoutId id, UUID driverId, BigDecimal amount, String currency, String bankAccountRef, Instant now) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (bankAccountRef == null || bankAccountRef.isBlank() || bankAccountRef.length() > 120) throw new IllegalArgumentException("A bank account reference is required");
        final var p = new Payout(id, driverId, amount, currency, bankAccountRef, now);
        p.raise(PayoutRequested.at(now, id.value(), driverId, amount, currency));
        return p;
    }

    public void submitted(String ref, Instant now) {
        if (status == Status.SUBMITTED && ref.equals(providerRef)) return;
        if (status != Status.REQUESTED) throw new InvalidStateException("Payout is " + status + " and cannot be submitted");
        status = Status.SUBMITTED;
        providerRef = ref;
        touch(now);
    }

    /** @return true if newly marked paid */
    public boolean paid(Instant now) {
        if (status == Status.PAID) return false;
        if (status == Status.FAILED) {
            throw new InvalidStateException("Payout was already reversed as failed: the provider now reports it paid, this needs a human");
        }
        status = Status.PAID;
        touch(now);
        raise(PayoutPaid.at(now, id().value(), driverId, amount));
        return true;
    }

    /** @return true if newly marked failed, i.e. the caller must now reverse the wallet debit */
    public boolean failed(String reason, Instant now) {
        if (status == Status.FAILED) return false;
        if (status == Status.PAID) throw new InvalidStateException("Payout is already paid and cannot fail");
        status = Status.FAILED;
        failureReason = reason == null ? "" : reason;
        touch(now);
        raise(PayoutFailed.at(now, id().value(), driverId, amount, failureReason));
        return true;
    }

    public UUID driverId() { return driverId; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public String bankAccountRef() { return bankAccountRef; }
    public Status status() { return status; }
    public String providerRef() { return providerRef; }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), driverId, amount, currency, bankAccountRef, status.name(), providerRef, failureReason, createdAt, updatedAt, version);
    }

    public static Payout restore(Snapshot s) {
        final var p = new Payout(PayoutId.of(s.id()), s.driverId(), s.amount(), s.currency(), s.bankAccountRef(), s.createdAt());
        p.status = Status.valueOf(s.status());
        p.providerRef = s.providerRef();
        p.failureReason = s.failureReason();
        p.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return p;
    }
}
