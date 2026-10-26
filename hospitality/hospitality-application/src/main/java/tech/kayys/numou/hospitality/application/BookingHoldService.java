package tech.kayys.numou.hospitality.application;

import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.StayBooking;
import tech.kayys.numou.hospitality.domain.StayBookingStatus;
import tech.kayys.numou.hospitality.domain.StaySearch;
import tech.kayys.numou.hospitality.spi.AtomicStayHoldPort;
import tech.kayys.numou.hospitality.spi.StayBookingRepository;

/** Creates a durable multi-night inventory hold from a previously generated quote. */
public final class BookingHoldService {
    private final CustomerStayService stays;
    private final StayBookingRepository bookings;
    private final AtomicStayHoldPort holds;
    private final Clock clock;
    private final Duration holdTtl;

    public BookingHoldService(CustomerStayService stays, StayBookingRepository bookings,
                              AtomicStayHoldPort holds, Clock clock, Duration holdTtl) {
        this.stays = Objects.requireNonNull(stays, "stays");
        this.bookings = Objects.requireNonNull(bookings, "bookings");
        this.holds = Objects.requireNonNull(holds, "holds");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.holdTtl = Objects.requireNonNull(holdTtl, "holdTtl");
        if (holdTtl.isZero() || holdTtl.isNegative()) {
            throw new IllegalArgumentException("holdTtl must be positive");
        }
    }

    public record CreateBooking(String tenantId, String customerId, StaySearch stay,
                                String currency, String idempotencyKey) { }

    public Uni<StayBooking> createHold(CreateBooking command) {
        Objects.requireNonNull(command, "command");
        requireText(command.tenantId(), "tenantId");
        requireText(command.customerId(), "customerId");
        requireText(command.idempotencyKey(), "idempotencyKey");
        Objects.requireNonNull(command.stay(), "stay");
        if (command.idempotencyKey().length() > 200) {
            throw new IllegalArgumentException("idempotencyKey must not exceed 200 characters");
        }
        return bookings.findByIdempotencyKey(command.tenantId(), command.customerId(), command.idempotencyKey())
                .flatMap(existing -> {
                    if (existing.isPresent()) {
                        StayBooking prior = existing.get();
                        if (!sameRequest(prior, command)) {
                            return Uni.createFrom().failure(new IdempotencyConflictException());
                        }
                        return Uni.createFrom().item(prior);
                    }
                    return stays.quote(command.tenantId(), command.stay(), command.currency()).flatMap(quote -> {
                        Instant now = clock.instant();
                        if (quote.isExpiredAt(now)) {
                            return Uni.createFrom().failure(new IllegalStateException(
                                    "Quote expired before booking hold creation"));
                        }
                        Instant expiry = min(quote.expiresAt(), now.plus(holdTtl));
                        StayBooking proposed = StayBooking.held(UUID.randomUUID(), command.tenantId(),
                                command.customerId(), quote, command.idempotencyKey(), now, expiry);
                        // The port owns the race between concurrent requests with the same key.
                        // It must atomically enforce key uniqueness and reserve all requested nights.
                        return holds.createHoldAtomically(proposed, expiry).flatMap(created -> {
                            if (!created.id().equals(proposed.id())
                                    || !created.tenantId().equals(command.tenantId())
                                    || !created.customerId().equals(command.customerId())
                                    || !created.idempotencyKey().equals(command.idempotencyKey())
                                    || !created.matches(quote, command.customerId())
                                    || created.status() != StayBookingStatus.HELD) {
                                return Uni.createFrom().failure(new IllegalStateException(
                                        "Atomic hold adapter returned a booking inconsistent with the request"));
                            }
                            return Uni.createFrom().item(created);
                        });
                    });
                });
    }

    private static boolean sameRequest(StayBooking prior, CreateBooking command) {
        StaySearch stay = command.stay();
        return prior.listingId().equals(stay.listingId())
                && prior.unitId().equals(stay.unitId())
                && prior.checkIn().equals(stay.checkIn())
                && prior.checkOut().equals(stay.checkOut())
                && prior.guestCount() == stay.guestCount();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
