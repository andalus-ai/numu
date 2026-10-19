package tech.kayys.payment.processor.bank.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.processor.bank.domain.BankTransferPayment;

/**
 * Repository for Bank Transfer payment transactions.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class BankTransferPaymentRepository implements PanacheRepository<BankTransferPayment> {

    /**
     * Find Bank Transfer payment by transaction ID
     */
    public Optional<BankTransferPayment> findByTransactionId(String transactionId) {
        return find("transactionId", transactionId).firstResultOptional();
    }

    /**
     * Find Bank Transfer payment by external order ID
     */
    public Optional<BankTransferPayment> findByExternalOrderId(String externalOrderId) {
        return find("externalOrderId", externalOrderId).firstResultOptional();
    }

    /**
     * Find Bank Transfer payment by external transaction ID (from gateway)
     */
    public Optional<BankTransferPayment> findByExternalTransactionId(String externalTransactionId) {
        return find("externalTransactionId", externalTransactionId).firstResultOptional();
    }

    /**
     * Find Bank Transfer payment by virtual account number
     */
    public Optional<BankTransferPayment> findByVirtualAccountNumber(String vaNumber) {
        return find("virtualAccountNumber", vaNumber).firstResultOptional();
    }

    /**
     * Find Bank Transfer payment by bank code
     */
    public List<BankTransferPayment> findByBankCode(String bankCode) {
        return list("bankCode", bankCode);
    }

    /**
     * Find pending Bank Transfer payments
     */
    public List<BankTransferPayment> findPending() {
        return list("status", BankTransferPayment.Status.PENDING);
    }

    /**
     * Find successful Bank Transfer payments
     */
    public List<BankTransferPayment> findSuccessful() {
        return list("status", BankTransferPayment.Status.SUCCESS);
    }

    /**
     * Find failed Bank Transfer payments
     */
    public List<BankTransferPayment> findFailed() {
        return list("status", BankTransferPayment.Status.FAILED);
    }

    /**
     * Find expired Bank Transfer payments
     */
    public List<BankTransferPayment> findExpired() {
        return list("status", BankTransferPayment.Status.EXPIRED);
    }

    /**
     * Find Bank Transfer payments by status
     */
    public List<BankTransferPayment> findByStatus(BankTransferPayment.Status status) {
        return list("status", status);
    }

    /**
     * Find Bank Transfer payments by payment method
     */
    public List<BankTransferPayment> findByPaymentMethod(String paymentMethod) {
        return list("paymentMethod", paymentMethod);
    }

    /**
     * Find Bank Transfer payments by customer email
     */
    public List<BankTransferPayment> findByCustomerEmail(String customerEmail) {
        return list("customerEmail", customerEmail);
    }

    /**
     * Find Bank Transfer payments created within a time range
     */
    public List<BankTransferPayment> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return list("createdAt between ?1 and ?2", startDate, endDate);
    }

    /**
     * Find pending payments that have expired
     */
    public List<BankTransferPayment> findExpiredPending(LocalDateTime expiryThreshold) {
        return list("status = ?1 and expiryTime < ?2", 
                   BankTransferPayment.Status.PENDING, expiryThreshold);
    }

    /**
     * Count Bank Transfer payments by status
     */
    public long countByStatus(BankTransferPayment.Status status) {
        return count("status", status);
    }

    /**
     * Count Bank Transfer payments by payment method
     */
    public long countByPaymentMethod(String paymentMethod) {
        return count("paymentMethod", paymentMethod);
    }

    /**
     * Count Bank Transfer payments by bank code
     */
    public long countByBankCode(String bankCode) {
        return count("bankCode", bankCode);
    }

    /**
     * Delete expired Bank Transfer payments older than specified date
     */
    public long deleteExpiredOlderThan(LocalDateTime date) {
        return delete("status = ?1 and createdAt < ?2", 
                     BankTransferPayment.Status.EXPIRED, date);
    }

    /**
     * Find Bank Transfer payments by gateway provider
     */
    public List<BankTransferPayment> findByGatewayProvider(String gatewayProvider) {
        return list("gatewayProvider", gatewayProvider);
    }

    /**
     * Find payments by bank and status
     */
    public List<BankTransferPayment> findByBankAndStatus(String bankCode, BankTransferPayment.Status status) {
        return find("bankCode = ?1 and status = ?2", bankCode, status).list();
    }
}
