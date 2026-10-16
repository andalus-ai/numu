package tech.kayys.tax.service;

import tech.kayys.tax.entity.Company;
import tech.kayys.tax.entity.TaxCalculation;
import tech.kayys.tax.entity.TaxPayment;
import tech.kayys.tax.dto.TaxPaymentRequest;
import tech.kayys.tax.dto.TaxPaymentResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class TaxPaymentService {
    
    private static final Logger LOG = Logger.getLogger(TaxPaymentService.class.getName());
    
    @Transactional
    public TaxPaymentResult processPayment(TaxPaymentRequest request) {
        LOG.info("Processing tax payment for calculation ID: " + request.taxCalculationId);
        
        TaxCalculation calculation = TaxCalculation.findById(request.taxCalculationId);
        if (calculation == null) {
            throw new IllegalArgumentException("Tax calculation not found");
        }
        
        // Validate payment amount
        if (request.paymentAmount.compareTo(calculation.totalTaxLiability) != 0) {
            throw new IllegalArgumentException("Payment amount does not match tax liability");
        }
        
        // Create payment record
        TaxPayment payment = new TaxPayment();
        payment.company = calculation.company;
        payment.taxCalculation = calculation;
        payment.paymentAmount = request.paymentAmount;
        payment.paymentDate = request.paymentDate;
        payment.paymentMethod = request.paymentMethod;
        payment.referenceNumber = request.referenceNumber;
        payment.bankCode = request.bankCode;
        payment.notes = request.notes;
        
        // Calculate penalties if payment is late
        if (request.paymentDate.isAfter(calculation.dueDate)) {
            payment.penaltyAmount = calculateLatePenalty(calculation.totalTaxLiability, 
                calculation.dueDate, request.paymentDate);
            payment.interestAmount = calculateLateInterest(calculation.totalTaxLiability, 
                calculation.dueDate, request.paymentDate);
        }
        
        payment.persist();
        
        // Update calculation status
        calculation.status = TaxCalculation.CalculationStatus.PAID;
        calculation.persist();
        
        LOG.info("Tax payment processed successfully for company: " + calculation.company.npwp);
        
        return new TaxPaymentResult(payment.id, payment.paymentAmount, payment.paymentDate, 
            payment.status, payment.penaltyAmount, payment.interestAmount, payment.referenceNumber);
    }
    
    private BigDecimal calculateLatePenalty(BigDecimal taxAmount, LocalDate dueDate, LocalDate paymentDate) {
        long daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDate, paymentDate);
        if (daysLate > 0) {
            // 2% per month penalty
            BigDecimal monthsLate = BigDecimal.valueOf(daysLate).divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP);
            return taxAmount.multiply(new BigDecimal("0.02")).multiply(monthsLate);
        }
        return BigDecimal.ZERO;
    }
    
    private BigDecimal calculateLateInterest(BigDecimal taxAmount, LocalDate dueDate, LocalDate paymentDate) {
        long daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDate, paymentDate);
        if (daysLate > 0) {
            // 1.5% per month interest
            BigDecimal monthsLate = BigDecimal.valueOf(daysLate).divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP);
            return taxAmount.multiply(new BigDecimal("0.015")).multiply(monthsLate);
        }
        return BigDecimal.ZERO;
    }
    
    @Transactional
    public void confirmPayment(Long paymentId, String confirmationNumber) {
        TaxPayment payment = TaxPayment.findById(paymentId);
        if (payment == null) {
            throw new IllegalArgumentException("Payment not found");
        }
        
        payment.status = TaxPayment.PaymentStatus.CONFIRMED;
        payment.confirmedDate = LocalDateTime.now();
        payment.referenceNumber = confirmationNumber;
        payment.persist();
        
        LOG.info("Payment confirmed for payment ID: " + paymentId);
    }
    
    public List<TaxPayment> getPaymentHistory(String npwp) {
        Company company = Company.findByNpwp(npwp);
        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }
        
        return TaxPayment.list("company = ?1 order by paymentDate desc", company);
    }
}
