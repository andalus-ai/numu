package tech.kayys.numou.mobility.pricing;

import tech.kayys.numou.mobility.kernel.domain.VehicleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Quotes and finalises fares.
 *
 * <p><b>Upfront vs metered.</b> Riders see one price before booking and that price stands, as long as the trip stays
 * within tolerance of the estimate (distance 70%-120%, time 60%-150%). Outside the band - the rider changed the route,
 * hit a road closure, or got out early - the fare is recalculated from the actual trip with the <em>surge multiplier locked
 * at booking time</em>, and never exceeds 1.5x the quote (rider protection against runaway detours).
 */
public final class FareCalculator {

    static final BigDecimal DIST_LOW = new BigDecimal("0.70"), DIST_HIGH = new BigDecimal("1.20");
    static final BigDecimal TIME_LOW = new BigDecimal("0.60"), TIME_HIGH = new BigDecimal("1.50");
    static final BigDecimal CAP_FACTOR = new BigDecimal("1.5");

    private FareCalculator() { }

    public static FareQuote quote(FareSchedule schedule, VehicleType vehicle, double distanceMeters, long durationSeconds,
                                  BigDecimal surgeMultiplier, Promo promo, Instant now, Duration ttl) {
        if (distanceMeters <= 0 || durationSeconds < 0) {
            throw new IllegalArgumentException("Trip must have a positive distance");
        }
        if (surgeMultiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Surge multiplier cannot be below 1");
        }
        final Rate r = schedule.rate(vehicle);
        final BigDecimal km = BigDecimal.valueOf(distanceMeters).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        final BigDecimal minutes = BigDecimal.valueOf(durationSeconds).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        final BigDecimal base = schedule.scale(r.base());
        final BigDecimal dist = schedule.scale(r.perKm().multiply(km));
        final BigDecimal time = schedule.scale(r.perMinute().multiply(minutes));
        final BigDecimal rawSum = base.add(dist).add(time);
        final BigDecimal afterMin = rawSum.max(schedule.scale(r.minimumFare()));
        final BigDecimal minAdj = afterMin.subtract(rawSum);
        final BigDecimal subtotal = schedule.roundUp(afterMin.multiply(surgeMultiplier));
        final BigDecimal surgeAmount = subtotal.subtract(afterMin);
        final BigDecimal discount = promo == null ? BigDecimal.ZERO.setScale(schedule.fractionDigits()) : promo.discount(subtotal, now, schedule.fractionDigits());
        final BigDecimal fee = schedule.scale(r.bookingFee());
        final BigDecimal total = subtotal.subtract(discount).add(fee);
        return new FareQuote(UUID.randomUUID(), vehicle, distanceMeters, durationSeconds, schedule.currency(), schedule.fractionDigits(),
                base, dist, time, minAdj, surgeMultiplier, surgeAmount, subtotal, promo, discount, fee, total, now, now.plus(ttl));
    }

    public static FinalFare finalFare(FareQuote q, FareSchedule schedule, double actualMeters, long actualSeconds, Instant now) {
        final BigDecimal distRatio = ratio(BigDecimal.valueOf(actualMeters), BigDecimal.valueOf(q.distanceMeters()));
        final BigDecimal timeRatio = q.durationSeconds() == 0 ? BigDecimal.ONE
                : ratio(BigDecimal.valueOf(actualSeconds), BigDecimal.valueOf(q.durationSeconds()));
        final boolean withinBand = distRatio.compareTo(DIST_LOW) >= 0 && distRatio.compareTo(DIST_HIGH) <= 0
                && timeRatio.compareTo(TIME_LOW) >= 0 && timeRatio.compareTo(TIME_HIGH) <= 0;
        if (withinBand) {
            return new FinalFare(FinalFare.Basis.UPFRONT, q.subtotal(), q.discount(), q.bookingFee(), q.total());
        }
        final Rate r = schedule.rate(q.vehicle());
        final BigDecimal km = BigDecimal.valueOf(actualMeters).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        final BigDecimal minutes = BigDecimal.valueOf(actualSeconds).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        final BigDecimal raw = schedule.scale(r.base()).add(schedule.scale(r.perKm().multiply(km))).add(schedule.scale(r.perMinute().multiply(minutes)));
        BigDecimal subtotal = schedule.roundUp(raw.max(schedule.scale(r.minimumFare())).multiply(q.surgeMultiplier()));
        final BigDecimal cap = schedule.roundUp(q.subtotal().multiply(CAP_FACTOR));
        if (subtotal.compareTo(cap) > 0) {
            subtotal = cap;
        }
        final BigDecimal discount = q.promo() == null ? BigDecimal.ZERO.setScale(schedule.fractionDigits()) : q.promo().discount(subtotal, q.createdAt(), schedule.fractionDigits());
        return new FinalFare(FinalFare.Basis.METERED, subtotal, discount, q.bookingFee(), subtotal.subtract(discount).add(q.bookingFee()));
    }

    private static BigDecimal ratio(BigDecimal actual, BigDecimal estimate) {
        return actual.divide(estimate, 6, RoundingMode.HALF_UP);
    }
}
