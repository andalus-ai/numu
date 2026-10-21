package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.ride.spi.PaymentsPort;
import tech.kayys.numou.mobility.ride.spi.RideRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Customer-support money tools, for operations staff only (the caller has already been authorised and is audited).
 *
 * <p>A refund is first recorded on the ride, which enforces the two invariants (idempotent per {@code ref}; total refunded never exceeds
 * what the rider paid), and only then moved through the wallets. If the money step fails the ride already says "refunded"; repeating the
 * same request finishes the money (every wallet operation carries a reference derived from {@code ref}), so a retry can never pay twice.
 *
 * <p>Who bears the cost: by default the platform. {@code driverShare} claws part of it back from the driver (e.g. a genuine driver fault);
 * it is debited like cash commission, so it can push the driver into the cash-debt limit but never blocks the refund.
 */
public final class SupportService {

    private static final int MAX_ATTEMPTS = 5;

    public record RefundResult(BigDecimal refunded, BigDecimal paid, BigDecimal refundable) { }

    private final RideRepository rides;
    private final PaymentsPort payments;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public SupportService(RideRepository rides, PaymentsPort payments, UnitOfWork uow, DomainEventSink events, DomainClock clock) {
        this.rides = rides;
        this.payments = payments;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
    }

    /** @param ref support-case reference; makes the refund idempotent (retrying the same case never double-refunds) */
    public Uni<RefundResult> refund(UUID tenantId, UUID rideId, BigDecimal amount, BigDecimal driverShare, String ref) {
        if (ref == null || ref.isBlank() || ref.length() > 80) {
            return Uni.createFrom().failure(new BusinessRuleViolation("A support case reference is required"));
        }
        final BigDecimal share = driverShare == null ? BigDecimal.ZERO : driverShare;
        if (share.signum() < 0 || share.compareTo(amount) > 0) {
            return Uni.createFrom().failure(new BusinessRuleViolation("The driver share must be between zero and the refund amount"));
        }
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> rides.find(tenantId, RideId.of(rideId))
                .map(o -> o.orElseThrow(() -> new NotFoundException("Ride", rideId))).flatMap(ride -> {
                    if (share.signum() > 0 && ride.driverId().isEmpty()) {
                        return Uni.<RefundResult>createFrom().failure(new BusinessRuleViolation("This ride has no driver to charge"));
                    }
                    final boolean applied = ride.recordRefund(ref, amount, clock.now());
                    final BigDecimal paid = ride.finalFare() != null ? ride.finalFare().total() : ride.cancelFee();
                    final var result = new RefundResult(ride.refunded(), paid, paid.subtract(ride.refunded()));
                    final Uni<Void> persist = !applied ? Uni.createFrom().voidItem() : rides.save(tenantId, ride).flatMap(x -> {
                        final var raised = ride.pullDomainEvents();
                        return raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised);
                    });
                    final String base = "refund:" + ref;
                    final BigDecimal platformPart = amount.subtract(share);
                    // money moves whether or not the refund was newly recorded: this is what makes a half-finished refund repairable by repeating it
                    return persist.flatMap(x -> payments.creditRider(tenantId, ride.riderId().value(), amount, base + ":rider", "refund"))
                            .flatMap(x -> platformPart.signum() == 0 ? Uni.createFrom().voidItem()
                                    : payments.movePlatform(tenantId, ride.id().value(), platformPart.negate(), base + ":platform", "refund"))
                            .flatMap(x -> share.signum() == 0 ? Uni.createFrom().voidItem()
                                    : payments.moveDriver(tenantId, ride.driverId().get(), share.negate(), base + ":driver", "refund clawback"))
                            .replaceWith(result);
                })));
    }
}
