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
@Table(name = "tax_calculations")
public class TaxCalculation extends PanacheEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    public Company company;
    
    @NotNull
    @Column(name = "tax_year")
    public Integer taxYear;
    
    @NotNull
    @Column(name = "tax_period")
    public String taxPeriod; // Monthly, Quarterly, Annual
    
    @NotNull
    @Column(name = "gross_income", precision = 19, scale = 2)
    public BigDecimal grossIncome;
    
    @NotNull
    @Column(name = "deductible_expenses", precision = 19, scale = 2)
    public BigDecimal deductibleExpenses;
    
    @NotNull
    @Column(name = "taxable_income", precision = 19, scale = 2)
    public BigDecimal taxableIncome;
    
    @NotNull
    @Column(name = "tax_rate", precision = 5, scale = 4)
    public BigDecimal taxRate;
    
    @NotNull
    @Column(name = "tax_amount", precision = 19, scale = 2)
    public BigDecimal taxAmount;
    
    @Column(name = "pph_final_amount", precision = 19, scale = 2)
    public BigDecimal pphFinalAmount;
    
    @Column(name = "vat_amount", precision = 19, scale = 2)
    public BigDecimal vatAmount;
    
    @Column(name = "withholding_tax_amount", precision = 19, scale = 2)
    public BigDecimal withholdingTaxAmount;
    
    @Column(name = "total_tax_liability", precision = 19, scale = 2)
    public BigDecimal totalTaxLiability;
    
    @Column(name = "calculation_date")
    public LocalDateTime calculationDate;
    
    @Column(name = "due_date")
    public LocalDate dueDate;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    public CalculationStatus status = CalculationStatus.CALCULATED;
    
    @Column(name = "notes")
    public String notes;
    
    @Column(name = "calculated_by")
    public String calculatedBy;
    
    @Column(name = "reviewed_by")
    public String reviewedBy;
    
    @Column(name = "reviewed_date")
    public LocalDateTime reviewedDate;
    
    @PrePersist
    public void prePersist() {
        this.calculationDate = LocalDateTime.now();
        calculateDueDate();
    }
    
    private void calculateDueDate() {
        if ("MONTHLY".equals(this.taxPeriod)) {
            this.dueDate = LocalDate.of(this.taxYear, 
                this.taxYear == LocalDate.now().getYear() ? LocalDate.now().getMonthValue() : 12, 20);
        } else if ("QUARTERLY".equals(this.taxPeriod)) {
            this.dueDate = LocalDate.of(this.taxYear, 
                this.taxYear == LocalDate.now().getYear() ? ((LocalDate.now().getMonthValue() - 1) / 3 + 1) * 3 : 12, 20);
        } else {
            this.dueDate = LocalDate.of(this.taxYear + 1, 3, 31); // Annual due date
        }
    }
    
    public enum CalculationStatus {
        CALCULATED, REVIEWED, APPROVED, FILED, PAID
    }
}
