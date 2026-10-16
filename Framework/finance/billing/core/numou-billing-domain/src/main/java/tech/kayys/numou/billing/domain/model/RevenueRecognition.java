package tech.kayys.numou.billing.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.billing.domain.identifier.RevenueRecognitionId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Revenue Recognition aggregate root.
 * Tracks revenue recognition for subscription and service revenue.
 */
public final class RevenueRecognition extends AbstractAggregateRoot<RevenueRecognitionId> {
    
    private static final long serialVersionUID = 1L;
    
    private String contractId;
    private String customerId;
    private Money totalContractValue;
    private String currencyCode;
    private Instant contractStartDate;
    private Instant contractEndDate;
    private RevenueScheduleType scheduleType; // STRAIGHT_LINE, USAGE_BASED, MILESTONE
    private List<RevenueSchedule> schedules;
    private Money recognizedRevenue;
    private Money deferredRevenue;
    private RevenueStatus status;
    private String accountingPeriod;
    private boolean active;

    private RevenueRecognition(RevenueRecognitionId id) {
        super(id);
        this.schedules = new ArrayList<>();
        this.recognizedRevenue = Money.zero("USD");
        this.deferredRevenue = Money.zero("USD");
        this.status = RevenueStatus.PENDING;
        this.active = true;
    }

    private RevenueRecognition() {
        super();
    }

    /**
     * Factory method to create a new revenue recognition contract.
     */
    public static RevenueRecognition create(
            RevenueRecognitionId id,
            String contractId,
            String customerId,
            Money totalContractValue,
            String currencyCode,
            Instant contractStartDate,
            Instant contractEndDate,
            RevenueScheduleType scheduleType) {
        RevenueRecognition recognition = new RevenueRecognition(id);
        recognition.contractId = contractId;
        recognition.customerId = customerId;
        recognition.totalContractValue = totalContractValue;
        recognition.currencyCode = currencyCode;
        recognition.contractStartDate = contractStartDate;
        recognition.contractEndDate = contractEndDate;
        recognition.scheduleType = scheduleType;
        recognition.deferredRevenue = totalContractValue;
        recognition.recognizedRevenue = Money.zero(currencyCode);
        return recognition;
    }

    /**
     * Generates revenue schedules based on the contract.
     */
    public void generateSchedules() {
        if (scheduleType == RevenueScheduleType.STRAIGHT_LINE) {
            generateStraightLineSchedule();
        } else if (scheduleType == RevenueScheduleType.USAGE_BASED) {
            // Usage-based schedules are generated based on actual usage
            // This would be implemented separately
        } else if (scheduleType == RevenueScheduleType.MILESTONE) {
            // Milestone-based schedules are generated based on milestones
        }
    }

    private void generateStraightLineSchedule() {
        long daysBetween = ChronoUnit.DAYS.between(
            contractStartDate.atZone(ZoneId.systemDefault()).toLocalDate(),
            contractEndDate.atZone(ZoneId.systemDefault()).toLocalDate()
        );
        
        if (daysBetween <= 0) {
            throw new IllegalStateException("Contract end date must be after start date");
        }

        // Calculate daily revenue
        Money dailyRevenue = totalContractValue.divide(
            java.math.BigDecimal.valueOf(daysBetween)
        );

        // Generate schedules for each month
        LocalDate currentDate = contractStartDate.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = contractEndDate.atZone(ZoneId.systemDefault()).toLocalDate();

        while (currentDate.isBefore(endDate)) {
            LocalDate monthEnd = currentDate.withDayOfMonth(
                currentDate.getMonth().length(currentDate.isLeapYear())
            );
            if (monthEnd.isAfter(endDate)) {
                monthEnd = endDate;
            }

            long daysInPeriod = ChronoUnit.DAYS.between(currentDate, monthEnd) + 1;
            Money periodRevenue = dailyRevenue.multiply(
                java.math.BigDecimal.valueOf(daysInPeriod)
            );

            RevenueSchedule schedule = new RevenueSchedule(
                currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                monthEnd.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                periodRevenue,
                RevenueScheduleStatus.PENDING
            );
            schedules.add(schedule);
            currentDate = monthEnd.plusDays(1);
        }
    }

    /**
     * Recognizes revenue for a specific period.
     */
    public Money recognizeRevenue(Instant periodStart, Instant periodEnd) {
        Money periodRevenue = Money.zero(currencyCode);
        List<RevenueSchedule> dueSchedules = schedules.stream()
            .filter(s -> s.getStatus() == RevenueScheduleStatus.PENDING)
            .filter(s -> s.getScheduleEnd().isBefore(periodEnd) || 
                         s.getScheduleEnd().equals(periodEnd))
            .collect(java.util.stream.Collectors.toList());

        for (RevenueSchedule schedule : dueSchedules) {
            schedule.recognize();
            periodRevenue = periodRevenue.add(schedule.getAmount());
        }

        this.recognizedRevenue = recognizedRevenue.add(periodRevenue);
        this.deferredRevenue = deferredRevenue.subtract(periodRevenue);

        // Check if all revenue is recognized
        if (deferredRevenue.isZero()) {
            this.status = RevenueStatus.COMPLETED;
        }

        setUpdatedAt(Instant.now());
        incrementVersion();
        return periodRevenue;
    }

    /**
     * Gets the revenue recognized to date.
     */
    public Money getRecognizedToDate() {
        return recognizedRevenue;
    }

    /**
     * Gets the remaining deferred revenue.
     */
    public Money getDeferredRevenue() {
        return deferredRevenue;
    }

    /**
     * Gets the recognition percentage.
     */
    public double getRecognitionPercentage() {
        if (totalContractValue.isZero()) {
            return 0.0;
        }
        return recognizedRevenue.getAmount()
            .divide(totalContractValue.getAmount(), 4, java.math.RoundingMode.HALF_UP)
            .multiply(java.math.BigDecimal.valueOf(100))
            .doubleValue();
    }

    // Getters
    public String getContractId() { return contractId; }
    public String getCustomerId() { return customerId; }
    public Money getTotalContractValue() { return totalContractValue; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getContractStartDate() { return contractStartDate; }
    public Instant getContractEndDate() { return contractEndDate; }
    public RevenueScheduleType getScheduleType() { return scheduleType; }
    public List<RevenueSchedule> getSchedules() { return Collections.unmodifiableList(schedules); }
    public Money getRecognizedRevenue() { return recognizedRevenue; }
    public RevenueStatus getStatus() { return status; }
    public String getAccountingPeriod() { return accountingPeriod; }
    public boolean isActive() { return active; }

    public void setAccountingPeriod(String accountingPeriod) {
        this.accountingPeriod = accountingPeriod;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "RevenueRecognition{" +
                "id=" + getId() +
                ", contractId='" + contractId + '\'' +
                ", recognized=" + recognizedRevenue +
                ", deferred=" + deferredRevenue +
                ", status=" + status +
                '}';
    }

    /**
     * Revenue schedule type enum.
     */
    public enum RevenueScheduleType {
        STRAIGHT_LINE("Straight Line - Equal recognition over time"),
        USAGE_BASED("Usage Based - Recognition based on actual usage"),
        MILESTONE("Milestone - Recognition at defined milestones");

        private final String description;

        RevenueScheduleType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Revenue status enum.
     */
    public enum RevenueStatus {
        PENDING("Pending - Not yet started"),
        IN_PROGRESS("In Progress - Revenue being recognized"),
        COMPLETED("Completed - All revenue recognized"),
        SUSPENDED("Suspended - Recognition paused");

        private final String description;

        RevenueStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Revenue schedule value object.
     */
    public static final class RevenueSchedule implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final Instant scheduleStart;
        private final Instant scheduleEnd;
        private final Money amount;
        private RevenueScheduleStatus status;
        private Instant recognizedAt;

        public RevenueSchedule(
                Instant scheduleStart,
                Instant scheduleEnd,
                Money amount,
                RevenueScheduleStatus status) {
            this.scheduleStart = scheduleStart;
            this.scheduleEnd = scheduleEnd;
            this.amount = amount;
            this.status = status;
            validate();
        }

        @Override
        public void validate() {
            if (scheduleStart == null || scheduleEnd == null) {
                throw new IllegalArgumentException("Schedule dates cannot be null");
            }
            if (scheduleEnd.isBefore(scheduleStart)) {
                throw new IllegalArgumentException("Schedule end must be after start");
            }
            if (amount == null || amount.isZero()) {
                throw new IllegalArgumentException("Amount must be positive");
            }
        }

        public Instant getScheduleStart() { return scheduleStart; }
        public Instant getScheduleEnd() { return scheduleEnd; }
        public Money getAmount() { return amount; }
        public RevenueScheduleStatus getStatus() { return status; }
        public Instant getRecognizedAt() { return recognizedAt; }

        public void recognize() {
            this.status = RevenueScheduleStatus.RECOGNIZED;
            this.recognizedAt = Instant.now();
        }

        @Override
        public String toString() {
            return "RevenueSchedule{" +
                    "start=" + scheduleStart +
                    ", end=" + scheduleEnd +
                    ", amount=" + amount +
                    ", status=" + status +
                    '}';
        }
    }

    /**
     * Revenue schedule status enum.
     */
    public enum RevenueScheduleStatus {
        PENDING("Pending"),
        RECOGNIZED("Recognized"),
        ADJUSTED("Adjusted");

        private final String description;

        RevenueScheduleStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}