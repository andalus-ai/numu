package tech.kayys.numou.billing.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Pro-ration calculator for partial periods. Days are counted in UTC so the result does not depend on the
 * server's time zone.
 */
public final class ProRationCalculator {

    /**
     * Calculates pro-rated amount for a partial period.
     */
    public static Money calculateProRated(
            Money fullAmount,
            Instant periodStart,
            Instant periodEnd,
            Instant proRateStart,
            Instant proRateEnd) {
        
        if (proRateStart.isBefore(periodStart) || proRateEnd.isAfter(periodEnd)) {
            throw new IllegalArgumentException("Pro-rated period must be within full period");
        }

        // Calculate days in full period
        long totalDays = ChronoUnit.DAYS.between(
            periodStart.atZone(ZoneOffset.UTC).toLocalDate(),
            periodEnd.atZone(ZoneOffset.UTC).toLocalDate()
        );

        // Calculate days in pro-rated period
        long proRatedDays = ChronoUnit.DAYS.between(
            proRateStart.atZone(ZoneOffset.UTC).toLocalDate(),
            proRateEnd.atZone(ZoneOffset.UTC).toLocalDate()
        );

        if (totalDays == 0 || proRatedDays == 0) {
            return Money.zero(fullAmount.getCurrency().getCurrencyCode());
        }

        // Calculate pro-rated amount
        BigDecimal ratio = BigDecimal.valueOf(proRatedDays)
            .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);
        
        return fullAmount.multiply(ratio);
    }

    /**
     * Calculates pro-rated amount for a partial month.
     */
    public static Money calculateMonthlyProRation(
            Money monthlyAmount,
            Instant activationDate,
            Instant billingDate) {
        
        LocalDate activation = activationDate.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate billing = billingDate.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate monthEnd = billing.withDayOfMonth(
            billing.getMonth().length(billing.isLeapYear())
        );

        long daysInMonth = ChronoUnit.DAYS.between(
            billing.withDayOfMonth(1), monthEnd
        ) + 1;

        // an activation before the month began still pays for the whole month and no more
        long daysUsed = Math.min(ChronoUnit.DAYS.between(activation, monthEnd) + 1, daysInMonth);

        if (daysUsed <= 0 || daysInMonth == 0) {
            return Money.zero(monthlyAmount.getCurrency().getCurrencyCode());
        }

        BigDecimal ratio = BigDecimal.valueOf(daysUsed)
            .divide(BigDecimal.valueOf(daysInMonth), 6, RoundingMode.HALF_UP);

        return monthlyAmount.multiply(ratio);
    }

    /**
     * Calculates prorated refund amount.
     */
    public static Money calculateRefundProRation(
            Money paidAmount,
            Instant serviceStart,
            Instant serviceEnd,
            Instant cancellationDate) {
        
        if (cancellationDate.isBefore(serviceStart)) {
            return paidAmount;
        }

        if (cancellationDate.isAfter(serviceEnd)) {
            return Money.zero(paidAmount.getCurrency().getCurrencyCode());
        }

        long totalDays = ChronoUnit.DAYS.between(
            serviceStart.atZone(ZoneOffset.UTC).toLocalDate(),
            serviceEnd.atZone(ZoneOffset.UTC).toLocalDate()
        );

        long unusedDays = ChronoUnit.DAYS.between(
            cancellationDate.atZone(ZoneOffset.UTC).toLocalDate(),
            serviceEnd.atZone(ZoneOffset.UTC).toLocalDate()
        );

        if (totalDays == 0 || unusedDays <= 0) {
            return Money.zero(paidAmount.getCurrency().getCurrencyCode());
        }

        BigDecimal ratio = BigDecimal.valueOf(unusedDays)
            .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);

        return paidAmount.multiply(ratio);
    }

    private ProRationCalculator() {
        // Utility class
    }
}