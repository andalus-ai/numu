package tech.kayys.payment;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Repository for Payment entity
 */
@ApplicationScoped
public class PaymentRepository implements PanacheRepository<Payment> {
    
    /**
     * Find payment by transaction ID
     */
    public Optional<Payment> findByTransactionId(String transactionId) {
        return find("transactionId", transactionId).firstResultOptional();
    }
    
    /**
     * Find payments by invoice ID
     */
    public List<Payment> findByInvoiceId(Long invoiceId) {
        return list("invoice.id", invoiceId);
    }
    
    /**
     * Find payments by status
     */
    public List<Payment> findByStatus(Payment.PaymentStatus status) {
        return list("status", status);
    }
    
    /**
     * Find pending payments
     */
    public List<Payment> findPending() {
        return list("status", Payment.PaymentStatus.PENDING);
    }
    
    /**
     * Find payments by gateway provider
     */
    public List<Payment> findByGatewayProvider(String gatewayProvider) {
        return list("gatewayProvider", gatewayProvider);
    }
    
    /**
     * Find payments by payment method
     */
    public List<Payment> findByPaymentMethod(PaymentMethodType paymentMethod) {
        return list("paymentMethod", paymentMethod);
    }
    
    /**
     * Find payments by customer email
     */
    public List<Payment> findByCustomerEmail(String customerEmail) {
        return list("customerEmail", customerEmail);
    }
    
    /**
     * Find completed payments
     */
    public List<Payment> findCompleted() {
        return find("status = ?1 or status = ?2", 
            Payment.PaymentStatus.COMPLETED, 
            Payment.PaymentStatus.SUCCESS).list();
    }
    
    /**
     * Find failed payments
     */
    public List<Payment> findFailed() {
        return list("status", Payment.PaymentStatus.FAILED);
    }
    
    /**
     * Find refunded payments
     */
    public List<Payment> findRefunded() {
        return list("status", Payment.PaymentStatus.REFUNDED);
    }
    
    /**
     * Find payments by date range
     */
    public List<Payment> findByDateRange(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        return find("paymentDate between ?1 and ?2", startDate, endDate).list();
    }
    
    /**
     * Count payments by status
     */
    public long countByStatus(Payment.PaymentStatus status) {
        return count("status", status);
    }
    
    /**
     * Find payment by gateway transaction ID
     */
    public Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId) {
        return find("gatewayTransactionId", gatewayTransactionId).firstResultOptional();
    }
}