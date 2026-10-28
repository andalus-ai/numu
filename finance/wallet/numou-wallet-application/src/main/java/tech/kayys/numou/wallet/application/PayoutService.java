package tech.kayys.numou.wallet.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.wallet.domain.OwnerKind;
import tech.kayys.numou.wallet.domain.Payout;
import tech.kayys.numou.wallet.domain.PayoutId;
import tech.kayys.numou.wallet.spi.PaymentRepositories;
import tech.kayys.numou.wallet.spi.PayoutProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Driver withdrawals. Request debits the wallet immediately (the money cannot be withdrawn twice); a sweeper submits requested payouts to the
 * bank-transfer provider; the provider's callback marks them paid, or failed, in which case the debit is reversed exactly once.
 */
public final class PayoutService {

    public record PayoutView(UUID id, String status, BigDecimal amount, String currency) {
        static PayoutView of(Payout p) { return new PayoutView(p.id().value(), p.status().name(), p.amount(), p.currency()); }
    }

    private static final int MAX_ATTEMPTS = 6;

    private final PaymentRepositories.Payouts payouts;
    private final WalletService wallets;
    private final PayoutProvider provider;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final String currency;

    public PayoutService(PaymentRepositories.Payouts payouts, WalletService wallets, PayoutProvider provider, UnitOfWork uow, DomainEventSink events,
                         DomainClock clock, String currency) {
        this.payouts = payouts;
        this.wallets = wallets;
        this.provider = provider;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
        this.currency = currency;
    }

    /** Idempotent on {@code idempotencyKey}: a retried request returns the original payout and debits nothing more. */
    public Uni<PayoutView> request(UUID tenantId, UUID driverId, BigDecimal amount, String bankAccountRef, String idempotencyKey) {
        final var id = PayoutId.forKey(tenantId, driverId, idempotencyKey);
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> payouts.find(tenantId, id).flatMap(existing -> {
            if (existing.isPresent()) return Uni.createFrom().item(PayoutView.of(existing.get()));
            final var payout = Payout.request(id, driverId, amount, currency, bankAccountRef, clock.now());
            return wallets.requestPayout(tenantId, driverId, amount, "payout:" + id)          // debit first: refused if the balance is not enough
                    .flatMap(x -> payouts.save(tenantId, payout)).flatMap(x -> events.publish(tenantId, payout.pullDomainEvents()))
                    .replaceWith(PayoutView.of(payout));
        })));
    }

    /** For finance: what has to be transferred. Includes the bank account token, so only operations may see it. */
    public record PayoutOpsView(UUID id, UUID driverId, BigDecimal amount, String currency, String bankAccountRef, String status) { }

    public Uni<java.util.List<PayoutOpsView>> listByStatus(UUID tenantId, Payout.Status status, int limit) {
        return payouts.findByStatus(tenantId, status, Math.min(Math.max(limit, 1), 500)).map(list -> list.stream()
                .map(p -> new PayoutOpsView(p.id().value(), p.driverId(), p.amount(), p.currency(), p.bankAccountRef(), p.status().name())).toList());
    }

    /** Sweeper step: hand requested payouts to the provider. A provider failure leaves the payout REQUESTED for the next tick. */
    public Uni<Integer> submitDue(UUID tenantId, int limit) {
        return payouts.findByStatus(tenantId, Payout.Status.REQUESTED, limit).flatMap(list -> {
            final int[] n = {0};
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (final Payout p : list) {
                chain = chain.flatMap(x -> provider.disburse(tenantId, p).flatMap(ref -> mark(tenantId, p.id(), q -> q.submitted(ref, clock.now())))
                        .invoke(() -> n[0]++).onFailure().recoverWithItem((Void) null));
            }
            return chain.map(x -> n[0]);
        });
    }

    /** The provider's result callback (already authenticated by {@link PayoutProvider#parseResult}). */
    public Uni<Void> onResult(PayoutProvider.Result r) {
        final var id = PayoutId.of(r.payoutId());
        if (r.ok()) {
            return mark(r.tenantId(), id, p -> p.paid(clock.now()));
        }
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> payouts.find(r.tenantId(), id)
                .map(o -> o.orElseThrow(() -> new NotFoundException("Payout", r.payoutId()))).flatMap(p -> {
                    final boolean newlyFailed = p.failed(r.reason(), clock.now());
                    // The reversal credit is idempotent, so it runs every time: repeating it is how a half-finished reversal gets repaired.
                    return wallets.credit(r.tenantId(), OwnerKind.DRIVER, p.driverId(), p.amount(), "payout-reversal:" + id, "payout failed")
                            .flatMap(x -> newlyFailed ? payouts.save(r.tenantId(), p).flatMap(y -> events.publish(r.tenantId(), p.pullDomainEvents()))
                                    : Uni.createFrom().voidItem());
                })));
    }

    public Uni<Void> handleCallback(Map<String, String> lowerCaseHeaders, String body) {
        return onResult(provider.parseResult(lowerCaseHeaders, body, clock.now()));
    }

    private Uni<Void> mark(UUID tenantId, PayoutId id, java.util.function.Consumer<Payout> change) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> payouts.find(tenantId, id)
                .map(o -> o.orElseThrow(() -> new NotFoundException("Payout", id.value()))).flatMap(p -> {
                    change.accept(p);
                    return payouts.save(tenantId, p).flatMap(x -> events.publish(tenantId, p.pullDomainEvents()));
                })));
    }
}
