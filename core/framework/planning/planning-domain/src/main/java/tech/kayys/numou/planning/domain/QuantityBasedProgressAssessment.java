package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Quantity-derived activity progress; any physical quantity beyond plan remains explicit as overrun. */
public record QuantityBasedProgressAssessment(
        UUID tenantId, UUID quantityDefinitionId, UUID acceptedMeasurementId, ActivityId activityId,
        LocalDate asOfDate, BigDecimal plannedQuantity, BigDecimal acceptedQuantity,
        BigDecimal completionPercent, BigDecimal overrunQuantity, ProgressAssessment scheduleAssessment) {
    public QuantityBasedProgressAssessment {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(quantityDefinitionId);
        Objects.requireNonNull(acceptedMeasurementId); Objects.requireNonNull(activityId);
        Objects.requireNonNull(asOfDate); Objects.requireNonNull(plannedQuantity);
        Objects.requireNonNull(acceptedQuantity); Objects.requireNonNull(completionPercent);
        Objects.requireNonNull(overrunQuantity); Objects.requireNonNull(scheduleAssessment);
        if (plannedQuantity.signum() <= 0) throw new IllegalArgumentException("Planned quantity must be greater than zero");
        if (acceptedQuantity.signum() < 0) throw new IllegalArgumentException("Accepted quantity cannot be negative");
        if (completionPercent.signum() < 0 || completionPercent.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Completion percent must be between 0 and 100");
        if (overrunQuantity.signum() < 0) throw new IllegalArgumentException("Overrun quantity cannot be negative");
    }

    public static QuantityBasedProgressAssessment assess(UUID tenantId, UUID definitionId, UUID measurementId,
            ScheduleActivity activity, LocalDate asOfDate, BigDecimal plannedQuantity, BigDecimal acceptedQuantity) {
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(plannedQuantity, "Planned quantity cannot be null");
        Objects.requireNonNull(acceptedQuantity, "Accepted quantity cannot be null");
        if (plannedQuantity.signum() <= 0)
            throw new IllegalArgumentException("Quantity-based progress requires a positive planned quantity");
        if (acceptedQuantity.signum() < 0) throw new IllegalArgumentException("Accepted quantity cannot be negative");
        BigDecimal rawPercent = acceptedQuantity.multiply(new BigDecimal("100"))
                .divide(plannedQuantity, 6, RoundingMode.HALF_UP);
        BigDecimal percent = rawPercent.min(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overrun = acceptedQuantity.subtract(plannedQuantity).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
        ProgressAssessment schedule = ProgressAssessment.assess(activity, asOfDate, percent);
        return new QuantityBasedProgressAssessment(tenantId, definitionId, measurementId, activity.id(), asOfDate,
                plannedQuantity, acceptedQuantity, percent, overrun, schedule);
    }
}
