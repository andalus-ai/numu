package tech.kayys.payment.processor.qris.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.processor.qris.domain.QRISPayment;

/**
 * Repository for QRIS payment transactions.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class QRISPaymentRepository implements PanacheRepository<QRISPayment> {

    /**
     * Find QRIS payment by transaction ID
     */
    public Optional<QRISPayment> findByTransactionId(String transactionId) {
        return find("transactionId", transactionId).firstResultOptional();
    }

    /**
     * Find QRIS payment by external order ID
     */
    public Optional<QRISPayment> findByExternalOrderId(String externalOrderId) {
        return find("externalOrderId", externalOrderId).firstResultOptional();
    }

    /**
     * Find QRIS payment by external transaction ID (from gateway)
     */
    public Optional<QRISPayment> findByExternalTransactionId(String externalTransactionId) {
        return find("externalTransactionId", externalTransactionId).firstResultOptional();
    }

    /**
     * Find pending QRIS payments
     */
    public List<QRISPayment> findPending() {
        return list("status", QRISPayment.Status.PENDING);
    }

    /**
     * Find successful QRIS payments
     */
    public List<QRISPayment> findSuccessful() {
        return list("status", QRISPayment.Status.SUCCESS);
    }

    /**
     * Find failed QRIS payments
     */
    public List<QRISPayment> findFailed() {
        return list("status", QRISPayment.Status.FAILED);
    }

    /**
     * Find expired QRIS payments
     */
    public List<QRISPayment> findExpired() {
        return list("status", QRISPayment.Status.EXPIRED);
    }

    /**
     * Find QRIS payments by status
     */
    public List<QRISPayment> findByStatus(QRISPayment.Status status) {
        return list("status", status);
    }

    /**
     * Find QRIS payments by payment method
     */
    public List<QRISPayment> findByPaymentMethod(String paymentMethod) {
        return list("paymentMethod", paymentMethod);
    }

    /**
     * Find QRIS payments by customer email
     */
    public List<QRISPayment> findByCustomerEmail(String customerEmail) {
        return list("customerEmail", customerEmail);
    }

    /**
     * Find QRIS payments created within a time range
     */
    public List<QRISPayment> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return list("createdAt between ?1 and ?2", startDate, endDate);
    }

    /**
     * Find pending payments that have expired
     */
    public List<QRISPayment> findExpiredPending(LocalDateTime expiryThreshold) {
        return list("status = ?1 and expiryTime < ?2", 
                   QRISPayment.Status.PENDING, expiryThreshold);
    }

    /**
     * Count QRIS payments by status
     */
    public long countByStatus(QRISPayment.Status status) {
        return count("status", status);
    }

    /**
     * Count QRIS payments by payment method
     */
    public long countByPaymentMethod(String paymentMethod) {
        return count("paymentMethod", paymentMethod);
    }

    /**
     * Delete expired QRIS payments older than specified date
     */
    public long deleteExpiredOlderThan(LocalDateTime date) {
        return delete("status = ?1 and createdAt < ?2", 
                     QRISPayment.Status.EXPIRED, date);
    }

    /**
     * Find QRIS payments by gateway provider
     */
    public List<QRISPayment> findByGatewayProvider(String gatewayProvider) {
        return list("gatewayProvider", gatewayProvider);
    }
}
