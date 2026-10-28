package tech.kayys.numou.mobility.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tech.kayys.numou.aggregate.persistence.MeteredDomainEventSink;
import tech.kayys.numou.aggregate.persistence.OutboxDomainEventSink;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.driver.application.DriverService;
import tech.kayys.numou.mobility.integration.MobilityComposition;
import tech.kayys.numou.mobility.kernel.geo.HaversineRouting;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.ServiceArea;
import tech.kayys.numou.mobility.kernel.spi.RoutingPort;
import tech.kayys.numou.mobility.persistence.PgDriverLocationIndex;
import tech.kayys.numou.mobility.persistence.PgLedgerRepository;
import tech.kayys.numou.mobility.persistence.PgMarketStores;
import tech.kayys.numou.mobility.persistence.PgMobilityRepositories;
import tech.kayys.numou.mobility.ride.application.DispatchService;
import tech.kayys.numou.mobility.ride.application.RideService;
import tech.kayys.numou.mobility.ride.application.RideSettlementService;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;
import tech.kayys.numou.mobility.ride.application.ChatService;
import tech.kayys.numou.mobility.ride.application.RiskService;
import tech.kayys.numou.mobility.ride.application.SupportService;
import tech.kayys.numou.wallet.application.PayoutService;
import tech.kayys.numou.wallet.application.TopUpService;
import tech.kayys.numou.wallet.application.WalletService;
import tech.kayys.numou.wallet.application.payment.GenericHmacProvider;
import tech.kayys.numou.wallet.application.payment.MidtransProvider;
import tech.kayys.numou.wallet.application.payment.PaymentProvider;
import tech.kayys.numou.wallet.application.payment.XenditProvider;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.reliability.adapter.json.EventTypeRegistry;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.numou.reliability.spi.port.OutboxStorePort;

import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Composition root for the ride-hailing service on Quarkus: Postgres repositories, transactional outbox, metered event sink,
 * OSRM routing (falling back to the straight-line estimator), and the shared {@link MobilityComposition} that the tests also use.
 * NOT COMPILED in the authoring sandbox.
 */
@ApplicationScoped
public class MobilityProducer {

    @Inject MobilityConfig config;
    @Inject ObjectMapper mapper;
    @Inject UnitOfWork uow;
    @Inject OutboxStorePort outbox;
    @Inject MetricsPort metrics;
    @Inject Instance<RequestContextHolder> requestContext;
    @Inject EventTypeRegistry eventTypes;
    @Inject Vertx vertx;
    @Inject PgMobilityRepositories.Rides rides;
    @Inject PgMobilityRepositories.Locks locks;
    @Inject PgMobilityRepositories.Drivers drivers;
    @Inject PgMobilityRepositories.Wallets wallets;
    @Inject PgLedgerRepository ledger;
    @Inject PgDriverLocationIndex index;
    @Inject PgMarketStores.Demand demand;
    @Inject PgMarketStores.SurgeState surgeState;
    @Inject PgMarketStores.Promos promos;
    @Inject RideNotifications notifications;
    @Inject PaymentsConfig payments;
    @Inject PgMobilityRepositories.TopUps topUpRepo;
    @Inject PgMobilityRepositories.Payouts payoutRepo;
    @Inject HttpPaymentGateways gateway;
    @Inject tech.kayys.numou.mobility.persistence.PgTripTraceRepository traces;
    @Inject PgMobilityRepositories.Chats chats;

    @Produces @ApplicationScoped
    DomainClock clock() {
        return Instant::now;
    }

    @Produces @ApplicationScoped
    DomainEventSink eventSink() {
        eventTypes.registerFallback(JsonDomainEvent.class);
        return new MeteredDomainEventSink(new OutboxDomainEventSink(outbox, mapper, this::actor, this::correlationId), metrics);
    }

    @Produces @ApplicationScoped
    RoutingPort routing() {
        return config.routing().osrmUrl().<RoutingPort>map(url -> new OsrmRouting(vertx, url, config.routing().timeout())).orElseGet(HaversineRouting::new);
    }

    @Produces @Singleton
    MobilityComposition.Services services(DomainEventSink events, DomainClock clock, RoutingPort routing) {
        final byte[] secret = Base64.getDecoder().decode(config.secret());
        final byte[] pinSecret = Base64.getDecoder().decode(config.pinSecret().orElse(config.secret()));
        final var area = config.area();
        final var cfg = MobilityComposition.Config.indonesia(List.of(new ServiceArea(area.name(), new LatLng(area.lat(), area.lng()), area.radiusMeters())), secret, pinSecret);
        final var tuned = new MobilityComposition.Config(cfg.currency(), cfg.fractionDigits(), cfg.schedule(), cfg.surge(), cfg.cancellation(), cfg.areas(),
                cfg.ride(), config.commission(), config.driverCashDebtLimit(), config.minPayout(), secret, pinSecret);
        return MobilityComposition.build(tuned, new MobilityComposition.Ports(rides, locks, drivers, index, wallets, ledger, demand, surgeState, promos,
                notifications, routing, uow, events, clock, traces, chats));
    }

    @Produces @ApplicationScoped RideService rideService(MobilityComposition.Services s) { return s.rides(); }

    @Produces @ApplicationScoped DispatchService dispatchService(MobilityComposition.Services s) { return s.dispatch(); }

    @Produces @ApplicationScoped DriverService driverService(MobilityComposition.Services s) { return s.drivers(); }

    @Produces @ApplicationScoped WalletService walletService(MobilityComposition.Services s) { return s.wallets(); }

    @Produces @ApplicationScoped RideSettlementService settlementService(MobilityComposition.Services s) { return s.settlement(); }

    @Produces @ApplicationScoped ChatService chatService(MobilityComposition.Services s) { return s.chat(); }

    @Produces @ApplicationScoped SupportService supportService(MobilityComposition.Services s) { return s.support(); }

    @Produces @ApplicationScoped RiskService riskService(MobilityComposition.Services s) { return s.risk(); }

    /** Only providers with credentials configured are offered; a callback for any other provider is acknowledged and ignored. */
    @Produces @ApplicationScoped
    TopUpService topUpService(MobilityComposition.Services s, DomainEventSink events, DomainClock clock) {
        final Map<String, PaymentProvider> providers = new HashMap<>();
        payments.midtrans().serverKey().ifPresent(k -> providers.put("midtrans", new MidtransProvider(k)));
        payments.xendit().callbackToken().ifPresent(k -> providers.put("xendit", new XenditProvider(k)));
        payments.partner().secret().ifPresent(k -> providers.put("partner", new GenericHmacProvider("partner", k, Duration.ofMinutes(5))));
        return new TopUpService(topUpRepo, s.wallets(), gateway, providers, uow, events, clock, payments.topup().min(), payments.topup().max(),
                payments.topup().ttl(), "IDR");
    }

    @Produces @ApplicationScoped
    PayoutService payoutService(MobilityComposition.Services s, DomainEventSink events, DomainClock clock) {
        return new PayoutService(payoutRepo, s.wallets(), new ManualPayoutProvider(), uow, events, clock, "IDR");
    }

    private String actor() {
        try { return requestContext.get().actor() == null ? "system" : requestContext.get().actor(); } catch (RuntimeException e) { return "system"; }
    }

    private String correlationId() {
        try { return requestContext.get().correlationId() == null ? UUID.randomUUID().toString() : requestContext.get().correlationId(); }
        catch (RuntimeException e) { return UUID.randomUUID().toString(); }
    }
}
