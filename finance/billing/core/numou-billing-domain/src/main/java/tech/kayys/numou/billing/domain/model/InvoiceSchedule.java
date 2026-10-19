package tech.kayys.numou.billing.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Invoice schedule value object.
 * Defines when invoices should be generated.
 */
public final class InvoiceSchedule implements ValueObject {
    
    private static final long serialVersionUID = 1L;
    
    private final ScheduleType type;
    private final int dayOfMonth;
    private final int dayOfWeek;
    private final int intervalDays;
    private final Instant nextInvoiceDate;
    private final Instant lastInvoiceDate;

    public InvoiceSchedule(
            ScheduleType type,
            int dayOfMonth,
            int dayOfWeek,
            int intervalDays,
            Instant nextInvoiceDate,
            Instant lastInvoiceDate) {
        this.type = type;
        this.dayOfMonth = dayOfMonth;
        this.dayOfWeek = dayOfWeek;
        this.intervalDays = intervalDays;
        this.nextInvoiceDate = nextInvoiceDate;
        this.lastInvoiceDate = lastInvoiceDate;
        validate();
    }

    @Override
    public void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Schedule type cannot be null");
        }
        if (type == ScheduleType.MONTHLY && (dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new IllegalArgumentException("Invalid day of month: " + dayOfMonth);
        }
        if (type == ScheduleType.WEEKLY && (dayOfWeek < 1 || dayOfWeek > 7)) {
            throw new IllegalArgumentException("Invalid day of week: " + dayOfWeek);
        }
        if (type == ScheduleType.CUSTOM && intervalDays <= 0) {
            throw new IllegalArgumentException("Interval days must be positive for custom schedule");
        }
    }

    // Getters
    public ScheduleType getType() { return type; }
    public int getDayOfMonth() { return dayOfMonth; }
    public int getDayOfWeek() { return dayOfWeek; }
    public int getIntervalDays() { return intervalDays; }
    public Instant getNextInvoiceDate() { return nextInvoiceDate; }
    public Instant getLastInvoiceDate() { return lastInvoiceDate; }

    /**
     * Calculates the next invoice date from the current date.
     */
    public Instant calculateNextInvoiceDate(Instant fromDate) {
        LocalDate date = fromDate.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate nextDate = switch (type) {
            case MONTHLY -> {
                LocalDate target = date.withDayOfMonth(Math.min(dayOfMonth, date.lengthOfMonth()));
                if (target.isBefore(date) || target.equals(date)) {
                    target = target.plusMonths(1);
                    target = target.withDayOfMonth(Math.min(dayOfMonth, target.lengthOfMonth()));
                }
                yield target;
            }
            case WEEKLY -> {
                int daysToAdd = (dayOfWeek - date.getDayOfWeek().getValue() + 7) % 7;
                if (daysToAdd == 0) {
                    daysToAdd = 7;
                }
                yield date.plusDays(daysToAdd);
            }
            case BI_WEEKLY -> {
                if (lastInvoiceDate == null) {
                    // First billing date
                    yield date.plusDays(14);
                }
                yield lastInvoiceDate.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(14);
            }
            case QUARTERLY -> {
                LocalDate firstDay = date.withDayOfMonth(1);
                LocalDate target = firstDay.plusMonths(3);
                yield target.withDayOfMonth(Math.min(dayOfMonth, target.lengthOfMonth()));
            }
            case CUSTOM -> date.plusDays(intervalDays);
        };
        return nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Checks if an invoice should be generated today.
     */
    public boolean isDue(Instant currentDate) {
        if (nextInvoiceDate == null) {
            return false;
        }
        return !currentDate.isBefore(nextInvoiceDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceSchedule that = (InvoiceSchedule) o;
        return type == that.type &&
               dayOfMonth == that.dayOfMonth &&
               dayOfWeek == that.dayOfWeek &&
               intervalDays == that.intervalDays &&
               Objects.equals(nextInvoiceDate, that.nextInvoiceDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, dayOfMonth, dayOfWeek, intervalDays, nextInvoiceDate);
    }

    @Override
    public String toString() {
        return "InvoiceSchedule{" +
                "type=" + type +
                ", nextInvoiceDate=" + nextInvoiceDate +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ScheduleType type = ScheduleType.MONTHLY;
        private int dayOfMonth = 1;
        private int dayOfWeek = 1;
        private int intervalDays = 30;
        private Instant nextInvoiceDate;
        private Instant lastInvoiceDate;

        public Builder type(ScheduleType type) {
            this.type = type;
            return this;
        }

        public Builder dayOfMonth(int dayOfMonth) {
            this.dayOfMonth = dayOfMonth;
            return this;
        }

        public Builder dayOfWeek(int dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
            return this;
        }

        public Builder intervalDays(int intervalDays) {
            this.intervalDays = intervalDays;
            return this;
        }

        public Builder nextInvoiceDate(Instant nextInvoiceDate) {
            this.nextInvoiceDate = nextInvoiceDate;
            return this;
        }

        public Builder lastInvoiceDate(Instant lastInvoiceDate) {
            this.lastInvoiceDate = lastInvoiceDate;
            return this;
        }

        public InvoiceSchedule build() {
            if (nextInvoiceDate == null) {
                nextInvoiceDate = Instant.now();
            }
            return new InvoiceSchedule(
                type, dayOfMonth, dayOfWeek, intervalDays, nextInvoiceDate, lastInvoiceDate
            );
        }
    }

    /**
     * Schedule type enum.
     */
    public enum ScheduleType {
        MONTHLY("Monthly"),
        WEEKLY("Weekly"),
        BI_WEEKLY("Bi-Weekly"),
        QUARTERLY("Quarterly"),
        CUSTOM("Custom");

        private final String displayName;

        ScheduleType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}