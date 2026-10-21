package tech.kayys.numou.mobility.integration;

import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.driver.application.DriverService;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.driver.spi.DriverRepository;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.ServiceArea;
import tech.kayys.numou.mobility.kernel.spi.RoutingPort;
import tech.kayys.numou.mobility.pricing.CancellationPolicy;
import tech.kayys.numou.mobility.pricing.FareSchedule;
import tech.kayys.numou.mobility.pricing.SurgePolicy;
import tech.kayys.numou.mobility.ride.application.DispatchService;
import tech.kayys.numou.mobility.ride.application.QuoteTokens;
import tech.kayys.numou.mobility.ride.application.RideConfig;
import tech.kayys.numou.mobility.ride.application.RidePins;
import tech.kayys.numou.mobility.ride.application.RideService;
import tech.kayys.numou.mobility.ride.application.RideSettlementService;
import tech.kayys.numou.mobility.ride.application.RideWrapUp;
import tech.kayys.numou.mobility.ride.application.ChatService;
import tech.kayys.numou.mobility.ride.application.RiskService;
import tech.kayys.numou.mobility.ride.application.SupportService;
import tech.kayys.numou.mobility.ride.application.SurgeService;
import tech.kayys.numou.mobility.ride.spi.ActiveRideLockRepository;
import tech.kayys.numou.mobility.ride.spi.ChatRepository;
import tech.kayys.numou.mobility.ride.spi.DemandCounter;
import tech.kayys.numou.mobility.ride.spi.PaymentsPort;
import tech.kayys.numou.mobility.ride.spi.PromoCatalog;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;
import tech.kayys.numou.mobility.ride.spi.RideRepository;
import tech.kayys.numou.mobility.ride.spi.SurgeStateStore;
import tech.kayys.numou.mobility.ride.spi.TripTraceRepository;
import tech.kayys.numou.mobility.ride.application.TripShareTokens;
import tech.kayys.numou.mobility.wallet.application.WalletService;
import tech.kayys.numou.mobility.wallet.spi.LedgerRepository;
import tech.kayys.numou.mobility.wallet.spi.WalletRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * The one place where the mobility services are wired together. Used by the Quarkus producer (with Postgres repositories)
 * and by the scenario tests (with in-memory ones), so what is tested is exactly what runs.
 *
 * <p>Wiring order avoids cycles: settlement, then wrap-up (needs settlement), then dispatch (needs wrap-up), then rides.
 */
public final class MobilityComposition {

    private MobilityComposition() { }

    public record Config(String currency, int fractionDigits, FareSchedule schedule, SurgePolicy surge, CancellationPolicy cancellation,
                         List<ServiceArea> areas, RideConfig ride, BigDecimal commissionRate, BigDecimal driverOverdraft,
                         BigDecimal minPayout, byte[] secret, byte[] pinSecret) {

        /** Jakarta-style defaults: IDR, 20% commission, 100,000 driver cash-debt limit. */
        public static Config indonesia(List<ServiceArea> areas, byte[] secret) {
            return indonesia(areas, secret, secret);
        }

        /**
         * @param secret    signs price quotes; safe to rotate at any time (open quotes simply expire within 5 minutes)
         * @param pinSecret derives pickup PINs; rotating it changes the PIN of every ride <em>currently in progress</em>, so rotate only
         *                  with no active rides (or run two instances of the key during a drain)
         */
        public static Config indonesia(List<ServiceArea> areas, byte[] secret, byte[] pinSecret) {
            return new Config("IDR", 0, FareSchedule.indonesiaDefaults(), SurgePolicy.defaults(), CancellationPolicy.defaults(), areas,
                    RideConfig.defaults(), new BigDecimal("0.20"), new BigDecimal("100000"), new BigDecimal("50000"), secret, pinSecret);
        }

        public static Config indonesiaAround(LatLng center, double radiusMeters, byte[] secret) {
            return indonesia(List.of(new ServiceArea("city", center, radiusMeters)), secret);
        }
    }

    public record Ports(RideRepository rides, ActiveRideLockRepository locks, DriverRepository drivers, DriverLocationIndex index,
                        WalletRepository wallets, LedgerRepository ledger, DemandCounter demand, SurgeStateStore surgeState, PromoCatalog promos,
                        RideNotifications notifications, RoutingPort routing, UnitOfWork uow, DomainEventSink events, DomainClock clock,
                        TripTraceRepository traces, ChatRepository chats) { }

    public record Services(DriverService drivers, WalletService wallets, RideService rides, DispatchService dispatch,
                           RideSettlementService settlement, QuoteTokens quoteTokens, PaymentsPort payments, SupportService support,
                           ChatService chat, RiskService risk) { }

    public static Services build(Config c, Ports p) {
        return build(c, p, java.util.function.UnaryOperator.identity());
    }

    /** @param decoratePayments wraps the payments port (fault injection in tests, metrics/tracing in production) */
    public static Services build(Config c, Ports p, java.util.function.UnaryOperator<PaymentsPort> decoratePayments) {
        final var driverService = new DriverService(p.drivers(), p.index(), p.uow(), p.events(), p.clock());
        final var walletService = new WalletService(p.wallets(), p.ledger(), p.uow(), p.events(), p.clock(), c.currency(), c.fractionDigits(),
                c.driverOverdraft(), c.minPayout());
        final PaymentsPort payments = decoratePayments.apply(new WalletBackedPayments(walletService));
        final var settlement = new RideSettlementService(p.rides(), payments, c.commissionRate(), c.cancellation(), p.uow(), p.events(), p.clock());
        final var risk = new RiskService(p.rides(), p.events(), p.clock());
        final var wrapUp = new RideWrapUp(p.locks(), driverService, p.promos(), p.notifications(), risk, settlement, p.uow(), p.clock());
        final var dispatch = new DispatchService(p.rides(), driverService, p.index(), p.routing(), payments, p.notifications(), wrapUp, c.ride(),
                p.uow(), p.events(), p.clock());
        final var surge = new SurgeService(p.demand(), p.index(), p.surgeState(), c.surge(), p.clock());
        final var tokens = new QuoteTokens(c.secret());
        final var rideService = new RideService(p.rides(), p.locks(), driverService, p.index(), payments, p.routing(), surge, p.promos(), tokens,
                new RidePins(c.pinSecret()), c.schedule(), c.cancellation(), c.areas(), c.ride(), p.uow(), p.events(), p.clock(), p.notifications(),
                wrapUp, dispatch, p.traces(), new TripShareTokens(c.secret()));
        return new Services(driverService, walletService, rideService, dispatch, settlement, tokens, payments,
                new SupportService(p.rides(), payments, p.uow(), p.events(), p.clock()),
                new ChatService(p.rides(), p.chats(), p.uow(), p.events(), p.clock(), p.notifications()), risk);
    }
}
