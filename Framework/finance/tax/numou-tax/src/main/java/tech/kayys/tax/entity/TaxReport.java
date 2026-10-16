package tech.kayys.tax.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_reports")
public class TaxReport extends PanacheEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    public Company company;
    
    @Column(name = "report_type")
    public String reportType;
    
    @NotNull
    @Column(name = "tax_year")
    public Integer taxYear;
    
    @Column(name = "filing_date")
    public LocalDate filingDate;
    
    @Column(name = "due_date")
    public LocalDate dueDate;
    
    @Column(name = "total_tax_amount", precision = 19, scale = 2)
    public BigDecimal totalTaxAmount;
    
    @Column(name = "submitted_by")
    public String submittedBy;
    
    @Column(name = "submission_date")
    public LocalDateTime submissionDate;
    
    @Column(name = "acknowledgment_number")
    public String acknowledgmentNumber;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    public ReportStatus status = ReportStatus.DRAFT;
    
    @Column(name = "notes")
    public String notes;
    
    @PrePersist
    public void prePersist() {
        if (this.filingDate == null) {
            this.filingDate = LocalDate.now();
        }
    }
    
    public enum ReportStatus {
        DRAFT, SUBMITTED, APPROVED, REJECTED
    }
}
