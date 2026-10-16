package tech.kayys.syirkah.purchasing.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Contract performance metrics.
 */
public final class ContractPerformance implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final String metricName;
    private final BigDecimal targetValue;
    private final BigDecimal actualValue;
    private final String uom;
    private final Instant measurementDate;
    private final String notes;
    private final boolean achieved;
    private final double percentageAchieved;

    public ContractPerformance(
            String metricName,
            BigDecimal targetValue,
            BigDecimal actualValue,
            String uom,
            Instant measurementDate,
            String notes,
            boolean achieved) {
        this.metricName = metricName;
        this.targetValue = targetValue;
        this.actualValue = actualValue;
        this.uom = uom;
        this.measurementDate = measurementDate;
        this.notes = notes;
        this.achieved = achieved;
        this.percentageAchieved = calculatePercentage();
        validate();
    }

    @Override
    public void validate() {
        if (metricName == null || metricName.trim().isEmpty()) {
            throw new IllegalArgumentException("Metric name cannot be empty");
        }
        if (targetValue == null || targetValue.signum() <= 0) {
            throw new IllegalArgumentException("Target value must be positive");
        }
        if (actualValue == null) {
            throw new IllegalArgumentException("Actual value cannot be null");
        }
        if (measurementDate == null) {
            throw new IllegalArgumentException("Measurement date cannot be null");
        }
    }

    private double calculatePercentage() {
        if (targetValue.signum() == 0) {
            return 0.0;
        }
        return actualValue.divide(targetValue, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }

    public String getMetricName() { return metricName; }
    public BigDecimal getTargetValue() { return targetValue; }
    public BigDecimal getActualValue() { return actualValue; }
    public String getUom() { return uom; }
    public Instant getMeasurementDate() { return measurementDate; }
    public String getNotes() { return notes; }
    public boolean isAchieved() { return achieved; }
    public double getPercentageAchieved() { return percentageAchieved; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractPerformance that = (ContractPerformance) o;
        return Objects.equals(metricName, that.metricName) &&
               Objects.equals(measurementDate, that.measurementDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricName, measurementDate);
    }

    @Override
    public String toString() {
        return "ContractPerformance{" +
                "metricName='" + metricName + '\'' +
                ", achieved=" + achieved +
                ", percentageAchieved=" + percentageAchieved + "%" +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String metricName;
        private BigDecimal targetValue;
        private BigDecimal actualValue;
        private String uom = "%";
        private Instant measurementDate;
        private String notes;
        private boolean achieved = false;

        public Builder metricName(String metricName) {
            this.metricName = metricName;
            return this;
        }

        public Builder targetValue(BigDecimal targetValue) {
            this.targetValue = targetValue;
            return this;
        }

        public Builder actualValue(BigDecimal actualValue) {
            this.actualValue = actualValue;
            return this;
        }

        public Builder uom(String uom) {
            this.uom = uom;
            return this;
        }

        public Builder measurementDate(Instant measurementDate) {
            this.measurementDate = measurementDate;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder achieved(boolean achieved) {
            this.achieved = achieved;
            return this;
        }

        public ContractPerformance build() {
            if (measurementDate == null) {
                measurementDate = Instant.now();
            }
            return new ContractPerformance(
                metricName, targetValue, actualValue, uom, measurementDate, notes, achieved
            );
        }
    }
}
