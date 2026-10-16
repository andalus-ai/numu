package tech.kayys.tax.dto;

import tech.kayys.tax.entity.TaxPayment;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TaxPaymentResult {
    
    public Long paymentId;
    public BigDecimal paymentAmount;
    public LocalDate paymentDate;
    public TaxPayment.PaymentStatus status;
    public BigDecimal penaltyAmount;
    public BigDecimal interestAmount;
    public String referenceNumber;
    
    public TaxPaymentResult() {}
    
    public TaxPaymentResult(Long paymentId, BigDecimal paymentAmount, LocalDate paymentDate,
                          TaxPayment.PaymentStatus status, BigDecimal penaltyAmount,
                          BigDecimal interestAmount, String referenceNumber) {
        this.paymentId = paymentId;
        this.paymentAmount = paymentAmount;
        this.paymentDate = paymentDate;
        this.status = status;
        this.penaltyAmount = penaltyAmount;
        this.interestAmount = interestAmount;
        this.referenceNumber = referenceNumber;
    }
}
