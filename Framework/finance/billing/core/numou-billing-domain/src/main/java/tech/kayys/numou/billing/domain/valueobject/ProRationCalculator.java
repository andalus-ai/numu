package tech.kayys.numou.billing.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Pro-ration calculator for partial periods.
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
            periodStart.atZone(ZoneId.systemDefault()).toLocalDate(),
            periodEnd.atZone(ZoneId.systemDefault()).toLocalDate()
        );

        // Calculate days in pro-rated period
        long proRatedDays = ChronoUnit.DAYS.between(
            proRateStart.atZone(ZoneId.systemDefault()).toLocalDate(),
            proRateEnd.atZone(ZoneId.systemDefault()).toLocalDate()
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
        
        LocalDate activation = activationDate.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate billing = billingDate.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate monthEnd = billing.withDayOfMonth(
            billing.getMonth().length(billing.isLeapYear())
        );

        long daysInMonth = ChronoUnit.DAYS.between(
            billing.withDayOfMonth(1), monthEnd
        ) + 1;

        long daysUsed = ChronoUnit.DAYS.between(activation, monthEnd) + 1;

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
            serviceStart.atZone(ZoneId.systemDefault()).toLocalDate(),
            serviceEnd.atZone(ZoneId.systemDefault()).toLocalDate()
        );

        long unusedDays = ChronoUnit.DAYS.between(
            cancellationDate.atZone(ZoneId.systemDefault()).toLocalDate(),
            serviceEnd.atZone(ZoneId.systemDefault()).toLocalDate()
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