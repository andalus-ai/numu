package tech.kayys.numou.wallet.domain;

import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.wallet.domain.event.TopUpMismatch;
import tech.kayys.numou.wallet.domain.event.TopUpPaid;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A rider's request to add money by an external payment. The wallet is credited <em>only</em> when the provider reports the payment as settled
 * for exactly the amount we asked for: a forged or garbled notification, or a different amount, never credits anything (an amount mismatch is
 * parked for operations). Money received after the intent expired is still credited: the rider has paid, the expiry only closed the payment page.
 */
public final class TopUpIntent extends VersionedAggregate<TopUpId> {

    private static final long serialVersionUID = 1L;

    public enum Status { PENDING, PAID, FAILED, EXPIRED, MISMATCH }

    public enum PaymentResult { CREDITED, DUPLICATE, MISMATCH }

    public record Snapshot(UUID id, UUID riderId, BigDecimal amount, String currency, String provider, String status, String providerRef,
                           Instant expiresAt, Instant paidAt, BigDecimal receivedAmount, Instant createdAt, Instant updatedAt, int version) { }

    private final UUID riderId;
    private final BigDecimal amount;
    private final String currency;
    private final String provider;
    private Status status = Status.PENDING;
    private String providerRef;
    private final Instant expiresAt;
    private Instant paidAt;
    private BigDecimal receivedAmount;

    private TopUpIntent(TopUpId id, UUID riderId, BigDecimal amount, String currency, String provider, Instant now, Instant expiresAt) {
        super(id, now);
        this.riderId = riderId;
        this.amount = amount;
        this.currency = currency;
        this.provider = provider;
        this.expiresAt = expiresAt;
    }

    public static TopUpIntent create(TopUpId id, UUID riderId, BigDecimal amount, String currency, String provider, Instant now, Duration ttl) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return new TopUpIntent(id, riderId, amount, currency, provider, now, now.plus(ttl));
    }

    /** Applies a "settled" notification. Idempotent: a redelivered notification is a DUPLICATE and credits nothing. */
    public PaymentResult markPaid(BigDecimal received, String ref, Instant now) {
        if (status == Status.PAID) {
            return PaymentResult.DUPLICATE;
        }
        if (received.compareTo(amount) != 0) {
            status = Status.MISMATCH;
            receivedAmount = received;
            providerRef = ref;
            touch(now);
            raise(TopUpMismatch.at(now, id().value(), riderId, amount, received, provider));
            return PaymentResult.MISMATCH;
        }
        status = Status.PAID;
        receivedAmount = received;
        providerRef = ref;
        paidAt = now;
        touch(now);
        raise(TopUpPaid.at(now, id().value(), riderId, amount, currency, provider));
        return PaymentResult.CREDITED;
    }

    public void markFailed(Instant now) {
        if (status == Status.PENDING) {
            status = Status.FAILED;
            touch(now);
        }
    }

    public void expire(Instant now) {
        if (status == Status.PENDING && !now.isBefore(expiresAt)) {
            status = Status.EXPIRED;
            touch(now);
        }
    }

    public UUID riderId() { return riderId; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public String provider() { return provider; }
    public Status status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public String providerRef() { return providerRef; }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), riderId, amount, currency, provider, status.name(), providerRef, expiresAt, paidAt, receivedAmount, createdAt, updatedAt, version);
    }

    public static TopUpIntent restore(Snapshot s) {
        final var t = new TopUpIntent(TopUpId.of(s.id()), s.riderId(), s.amount(), s.currency(), s.provider(), s.createdAt(), s.expiresAt());
        t.status = Status.valueOf(s.status());
        t.providerRef = s.providerRef();
        t.paidAt = s.paidAt();
        t.receivedAmount = s.receivedAmount();
        t.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return t;
    }
}
