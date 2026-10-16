package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.ReportId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conversion report aggregate root.
 * Tracks lead to customer conversion metrics.
 */
public final class ConversionReport extends AbstractAggregateRoot<ReportId> {
    
    private static final long serialVersionUID = 1L;
    
    private String reportName;
    private String period; // DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    private Instant periodStart;
    private Instant periodEnd;
    private int totalLeads;
    private int convertedLeads;
    private int lostLeads;
    private int qualifiedLeads;
    private double conversionRate;
    private List<ConversionSource> bySource;
    private List<ConversionIndustry> byIndustry;
    private String generatedBy;
    private Instant generatedAt;
    private String notes;

    private ConversionReport(ReportId id) {
        super(id);
        this.bySource = new ArrayList<>();
        this.byIndustry = new ArrayList<>();
        this.generatedAt = Instant.now();
    }

    private ConversionReport() {
        super();
    }

    /**
     * Factory method to create a new conversion report.
     */
    public static ConversionReport create(
            ReportId id,
            String reportName,
            String period,
            Instant periodStart,
            Instant periodEnd,
            String generatedBy) {
        ConversionReport report = new ConversionReport(id);
        report.reportName = reportName;
        report.period = period;
        report.periodStart = periodStart;
        report.periodEnd = periodEnd;
        report.generatedBy = generatedBy;
        return report;
    }

    /**
     * Adds conversion data by source.
     */
    public void addBySource(ConversionSource source) {
        bySource.add(source);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds conversion data by industry.
     */
    public void addByIndustry(ConversionIndustry industry) {
        byIndustry.add(industry);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Calculates conversion rate.
     */
    public void calculateConversionRate() {
        if (totalLeads == 0) {
            this.conversionRate = 0.0;
        } else {
            this.conversionRate = (double) convertedLeads / totalLeads * 100.0;
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getReportName() { return reportName; }
    public String getPeriod() { return period; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public int getTotalLeads() { return totalLeads; }
    public int getConvertedLeads() { return convertedLeads; }
    public int getLostLeads() { return lostLeads; }
    public int getQualifiedLeads() { return qualifiedLeads; }
    public double getConversionRate() { return conversionRate; }
    public List<ConversionSource> getBySource() { return Collections.unmodifiableList(bySource); }
    public List<ConversionIndustry> getByIndustry() { return Collections.unmodifiableList(byIndustry); }
    public String getGeneratedBy() { return generatedBy; }
    public Instant getGeneratedAt() { return generatedAt; }
    public String getNotes() { return notes; }

    public void setTotalLeads(int totalLeads) {
        this.totalLeads = totalLeads;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setConvertedLeads(int convertedLeads) {
        this.convertedLeads = convertedLeads;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setLostLeads(int lostLeads) {
        this.lostLeads = lostLeads;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setQualifiedLeads(int qualifiedLeads) {
        this.qualifiedLeads = qualifiedLeads;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "ConversionReport{" +
                "id=" + getId() +
                ", period='" + period + '\'' +
                ", totalLeads=" + totalLeads +
                ", convertedLeads=" + convertedLeads +
                ", conversionRate=" + conversionRate + "%" +
                '}';
    }

    /**
     * Conversion by source value object.
     */
    public static final class ConversionSource implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String source;
        private final int total;
        private final int converted;
        private final double rate;

        public ConversionSource(String source, int total, int converted) {
            this.source = source;
            this.total = total;
            this.converted = converted;
            this.rate = total > 0 ? (double) converted / total * 100.0 : 0.0;
            validate();
        }

        @Override
        public void validate() {
            if (source == null || source.trim().isEmpty()) {
                throw new IllegalArgumentException("Source cannot be empty");
            }
            if (total < 0) {
                throw new IllegalArgumentException("Total cannot be negative");
            }
            if (converted < 0 || converted > total) {
                throw new IllegalArgumentException("Converted must be between 0 and total");
            }
        }

        public String getSource() { return source; }
        public int getTotal() { return total; }
        public int getConverted() { return converted; }
        public double getRate() { return rate; }
    }

    /**
     * Conversion by industry value object.
     */
    public static final class ConversionIndustry implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String industry;
        private final int total;
        private final int converted;
        private final double rate;

        public ConversionIndustry(String industry, int total, int converted) {
            this.industry = industry;
            this.total = total;
            this.converted = converted;
            this.rate = total > 0 ? (double) converted / total * 100.0 : 0.0;
            validate();
        }

        @Override
        public void validate() {
            if (industry == null || industry.trim().isEmpty()) {
                throw new IllegalArgumentException("Industry cannot be empty");
            }
            if (total < 0) {
                throw new IllegalArgumentException("Total cannot be negative");
            }
            if (converted < 0 || converted > total) {
                throw new IllegalArgumentException("Converted must be between 0 and total");
            }
        }

        public String getIndustry() { return industry; }
        public int getTotal() { return total; }
        public int getConverted() { return converted; }
        public double getRate() { return rate; }
    }
}