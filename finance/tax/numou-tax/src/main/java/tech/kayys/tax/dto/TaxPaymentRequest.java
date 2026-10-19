package tech.kayys.tax.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TaxPaymentRequest {
    
    @NotNull
    public Long taxCalculationId;
    
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    public BigDecimal paymentAmount;
    
    @NotNull
    public LocalDate paymentDate;
    
    public String paymentMethod;
    public String referenceNumber;
    public String bankCode;
    public String notes;
    
    // Constructors
    public TaxPaymentRequest() {}
    
    public TaxPaymentRequest(Long taxCalculationId, BigDecimal paymentAmount, 
                           LocalDate paymentDate, String paymentMethod, 
                           String referenceNumber, String bankCode, String notes) {
        this.taxCalculationId = taxCalculationId;
        this.paymentAmount = paymentAmount;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.referenceNumber = referenceNumber;
        this.bankCode = bankCode;
        this.notes = notes;
    }
}
