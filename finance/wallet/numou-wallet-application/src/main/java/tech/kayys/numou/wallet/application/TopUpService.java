package tech.kayys.numou.wallet.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.wallet.application.payment.PaymentNotification;
import tech.kayys.numou.wallet.application.payment.PaymentProvider;
import tech.kayys.numou.wallet.domain.OrderIds;
import tech.kayys.numou.wallet.domain.OwnerKind;
import tech.kayys.numou.wallet.domain.TopUpId;
import tech.kayys.numou.wallet.domain.TopUpIntent;
import tech.kayys.numou.wallet.spi.PaymentGateway;
import tech.kayys.numou.wallet.spi.PaymentRepositories;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet top-ups through external payment providers.
 *
 * <p>Money is credited by exactly one path: a <em>verified</em> provider callback that says "settled" for exactly the intent's amount. The
 * credit carries the reference {@code topup:<intentId>}, so the same callback delivered once or six times, concurrently or a day later,
 * credits once. Unknown orders are acknowledged and ignored (never reveal what exists); amount mismatches are parked and never credited.
 */
public final class TopUpService {

    public enum Outcome { CREDITED, DUPLICATE, MISMATCH, FAILED, PENDING, IGNORED }

    public record TopUpView(String orderId, BigDecimal amount, String currency, Instant expiresAt, String paymentUrl) { }

    private static final int MAX_ATTEMPTS = 6;

    private final PaymentRepositories.TopUps intents;
    private final WalletService wallets;
    private final PaymentGateway gateway;
    private final Map<String, PaymentProvider> providers;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final BigDecimal min, max;
    private final Duration ttl;
    private final String currency;

    public TopUpService(PaymentRepositories.TopUps intents, WalletService wallets, PaymentGateway gateway, Map<String, PaymentProvider> providers,
                        UnitOfWork uow, DomainEventSink events, DomainClock clock, BigDecimal min, BigDecimal max, Duration ttl, String currency) {
        this.intents = intents;
        this.wallets = wallets;
        this.gateway = gateway;
        this.providers = Map.copyOf(providers);
        this.uow = uow;
        this.events = events;
        this.clock = clock;
        this.min = min;
        this.max = max;
        this.ttl = ttl;
        this.currency = currency;
    }

    public Uni<TopUpView> create(UUID tenantId, UUID riderId, BigDecimal amount, String provider) {
        if (!providers.containsKey(provider)) {
            return Uni.createFrom().failure(new BusinessRuleViolation("Unknown payment provider"));
        }
        if (amount == null || amount.compareTo(min) < 0 || amount.compareTo(max) > 0) {
            return Uni.createFrom().failure(new BusinessRuleViolation("Top-up must be between " + min.toPlainString() + " and " + max.toPlainString()));
        }
        final var id = TopUpId.generate();
        final var intent = TopUpIntent.create(id, riderId, amount, currency, provider, clock.now(), ttl);
        final String orderId = OrderIds.topUp(tenantId, id.value());
        return uow.execute(() -> intents.save(tenantId, intent)).flatMap(x -> gateway.createCharge(provider, orderId, amount, currency, riderId, ttl))
                .map(url -> new TopUpView(orderId, amount, currency, intent.expiresAt(), url));
    }

    /** Handles a provider callback. Throws {@code InvalidSignatureException} for anything not authentically from the provider. */
    public Uni<Outcome> onNotification(String providerId, Map<String, String> lowerCaseHeaders, String body) {
        final PaymentProvider provider = providers.get(providerId);
        if (provider == null) {
            return Uni.createFrom().item(Outcome.IGNORED);
        }
        final PaymentNotification n = provider.parse(lowerCaseHeaders, body, clock.now());      // verifies the signature before anything else
        final var ids = OrderIds.decodeTopUp(n.orderId());
        if (ids.isEmpty()) {
            return Uni.createFrom().item(Outcome.IGNORED);
        }
        final UUID tenant = ids.get().tenantId();
        final var intentId = TopUpId.of(ids.get().entityId());
        return switch (n.status()) {
            case PENDING -> Uni.createFrom().item(Outcome.PENDING);
            case FAILED, EXPIRED -> ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> intents.find(tenant, intentId).flatMap(o -> {
                if (o.isEmpty()) return Uni.createFrom().item(Outcome.IGNORED);
                o.get().markFailed(clock.now());
                return intents.save(tenant, o.get()).replaceWith(Outcome.FAILED);
            })));
            case SETTLED -> applyPaid(tenant, intentId, n);
        };
    }

    private Uni<Outcome> applyPaid(UUID tenant, TopUpId intentId, PaymentNotification n) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> intents.find(tenant, intentId).flatMap(o -> {
            if (o.isEmpty()) return Uni.createFrom().item(Outcome.IGNORED);
            final TopUpIntent intent = o.get();
            return switch (intent.markPaid(n.amount(), n.providerRef(), clock.now())) {
                case DUPLICATE -> Uni.createFrom().item(Outcome.DUPLICATE);
                case MISMATCH -> intents.save(tenant, intent).flatMap(x -> events.publish(tenant, intent.pullDomainEvents())).replaceWith(Outcome.MISMATCH);
                // credit first (idempotent on the intent id), then record it: a crash between the two is repaired by the provider's retry
                case CREDITED -> wallets.credit(tenant, OwnerKind.RIDER, intent.riderId(), intent.amount(), "topup:" + intentId, "top-up")
                        .flatMap(x -> intents.save(tenant, intent)).flatMap(x -> events.publish(tenant, intent.pullDomainEvents())).replaceWith(Outcome.CREDITED);
            };
        })));
    }
}
