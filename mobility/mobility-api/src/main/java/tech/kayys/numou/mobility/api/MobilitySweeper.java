package tech.kayys.numou.mobility.api;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.mobility.ride.application.DispatchService;
import tech.kayys.numou.mobility.ride.application.RideSettlementService;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.observability.SafeTags;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The safety nets behind the event-triggered paths: every few seconds expire lapsed offers / move on / end searches that ran out
 * of time; every ~30 seconds finish any ended ride whose settlement did not complete.
 *
 * <p>With N replicas each pod sweeps, so work is repeated N times per tick; optimistic locking makes that safe (a loser reloads and
 * finds nothing to do), only wasteful. Raise {@code sweep-interval} or add an advisory-lock leader election if it shows up in profiles.
 * NOT COMPILED in the authoring sandbox.
 */
@ApplicationScoped
public class MobilitySweeper {

    private static final Logger LOG = Logger.getLogger(MobilitySweeper.class);
    private static final MetricName OUTCOMES = MetricName.of("mobility.dispatch.sweep");
    private static final MetricName UNSETTLED = MetricName.of("mobility.settlement.repaired");

    @Inject MobilityConfig config;
    @Inject DispatchService dispatch;
    @Inject RideSettlementService settlement;
    @Inject MetricsPort metrics;
    @Inject tech.kayys.numou.mobility.wallet.application.PayoutService payouts;

    private final AtomicInteger ticks = new AtomicInteger();

    @Scheduled(every = "{numou.mobility.sweep-interval:3s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "mobility-sweep")
    Uni<Void> tick() {
        final boolean settleToo = ticks.incrementAndGet() % 10 == 0;
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (final UUID tenant : config.sweepTenants()) {
            chain = chain.flatMap(x -> dispatch.sweep(tenant).invoke(r -> {
                        metrics.increment(SafeTags.apply(OUTCOMES, SafeTags.of("outcome", "offered")), r.offered());
                        metrics.increment(SafeTags.apply(OUTCOMES, SafeTags.of("outcome", "no_drivers")), r.noDrivers());
                    }).replaceWithVoid().onFailure().invoke(t -> LOG.errorf(t, "dispatch sweep failed"))
                    .onFailure().recoverWithItem((Void) null));
            if (settleToo) {
                chain = chain.flatMap(x -> payouts.submitDue(tenant, 100).replaceWithVoid().onFailure().invoke(t -> LOG.errorf(t, "payout submission failed")).onFailure().recoverWithItem((Void) null));
                chain = chain.flatMap(x -> settlement.sweepUnsettled(tenant).invoke(n -> { if (n > 0) metrics.increment(UNSETTLED, n); })
                        .replaceWithVoid().onFailure().invoke(t -> LOG.errorf(t, "settlement sweep failed")).onFailure().recoverWithItem((Void) null));
            }
        }
        return chain;
    }
}
