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
@Table(name = "tax_payments")
public class TaxPayment extends PanacheEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    public Company company;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_calculation_id")
    public TaxCalculation taxCalculation;
    
    @NotNull
    @Column(name = "payment_amount", precision = 19, scale = 2)
    public BigDecimal paymentAmount;
    
    @NotNull
    @Column(name = "payment_date")
    public LocalDate paymentDate;
    
    @Column(name = "payment_method")
    public String paymentMethod;
    
    @Column(name = "reference_number")
    public String referenceNumber;
    
    @Column(name = "bank_code")
    public String bankCode;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    public PaymentStatus status = PaymentStatus.PENDING;
    
    @Column(name = "penalty_amount", precision = 19, scale = 2)
    public BigDecimal penaltyAmount = BigDecimal.ZERO;
    
    @Column(name = "interest_amount", precision = 19, scale = 2)
    public BigDecimal interestAmount = BigDecimal.ZERO;
    
    @Column(name = "notes")
    public String notes;
    
    @Column(name = "created_date")
    public LocalDateTime createdDate;
    
    @Column(name = "confirmed_date")
    public LocalDateTime confirmedDate;
    
    @PrePersist
    public void prePersist() {
        this.createdDate = LocalDateTime.now();
    }
    
    public enum PaymentStatus {
        PENDING, CONFIRMED, FAILED, CANCELLED
    }
}
