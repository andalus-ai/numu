package tech.kayys.payment.processor.bank.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.processor.bank.config.BankTransferConfig;
import tech.kayys.payment.processor.bank.domain.BankTransferPayment;
import tech.kayys.payment.processor.bank.repository.BankTransferPaymentRepository;

/**
 * Service class for Bank Transfer payment operations and business logic.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class BankTransferService {

    private static final Logger LOG = LoggerFactory.getLogger(BankTransferService.class);

    @Inject
    BankTransferPaymentRepository repository;

    @Inject
    BankTransferConfig bankTransferConfig;

    /**
     * Get Bank Transfer payment by transaction ID
     */
    public Optional<BankTransferPayment> getPayment(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    /**
     * Get Bank Transfer payment by external order ID
     */
    public Optional<BankTransferPayment> getPaymentByOrderId(String externalOrderId) {
        return repository.findByExternalOrderId(externalOrderId);
    }

    /**
     * Get Bank Transfer payment by virtual account number
     */
    public Optional<BankTransferPayment> getPaymentByVANumber(String vaNumber) {
        return repository.findByVirtualAccountNumber(vaNumber);
    }

    /**
     * Get all pending Bank Transfer payments
     */
    public List<BankTransferPayment> getPendingPayments() {
        return repository.findPending();
    }

    /**
     * Get all successful Bank Transfer payments
     */
    public List<BankTransferPayment> getSuccessfulPayments() {
        return repository.findSuccessful();
    }

    /**
     * Get Bank Transfer payments by status
     */
    public List<BankTransferPayment> getPaymentsByStatus(BankTransferPayment.Status status) {
        return repository.findByStatus(status);
    }

    /**
     * Get Bank Transfer payments by payment method variant
     */
    public List<BankTransferPayment> getPaymentsByMethod(PaymentMethodType methodType) {
        return repository.findByPaymentMethod(methodType.name());
    }

    /**
     * Get Bank Transfer payments within a date range
     */
    public List<BankTransferPayment> getPaymentsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByDateRange(startDate, endDate);
    }

    /**
     * Get today's Bank Transfer payments
     */
    public List<BankTransferPayment> getTodayPayments() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return repository.findByDateRange(startOfDay, endOfDay);
    }

    /**
     * Get statistics for Bank Transfer payments
     */
    public BankTransferPaymentStats getStats() {
        BankTransferPaymentStats stats = new BankTransferPaymentStats();
        stats.setTotalPayments(repository.count());
        stats.setPendingPayments(repository.countByStatus(BankTransferPayment.Status.PENDING));
        stats.setSuccessfulPayments(repository.countByStatus(BankTransferPayment.Status.SUCCESS));
        stats.setFailedPayments(repository.countByStatus(BankTransferPayment.Status.FAILED));
        stats.setExpiredPayments(repository.countByStatus(BankTransferPayment.Status.EXPIRED));
        stats.setCancelledPayments(repository.countByStatus(BankTransferPayment.Status.CANCELLED));
        stats.setRefundedPayments(repository.countByStatus(BankTransferPayment.Status.REFUNDED));
        return stats;
    }

    /**
     * Get statistics by bank
     */
    public BankTransferPaymentStats getStatsByBank(String bankCode) {
        BankTransferPaymentStats stats = new BankTransferPaymentStats();
        stats.setBankCode(bankCode);
        stats.setTotalPayments(repository.countByBankCode(bankCode));
        
        List<BankTransferPayment> allByBank = repository.findByBankCode(bankCode);
        stats.setPendingPayments(allByBank.stream()
            .filter(p -> p.getStatus() == BankTransferPayment.Status.PENDING).count());
        stats.setSuccessfulPayments(allByBank.stream()
            .filter(p -> p.getStatus() == BankTransferPayment.Status.SUCCESS).count());
        stats.setFailedPayments(allByBank.stream()
            .filter(p -> p.getStatus() == BankTransferPayment.Status.FAILED).count());
        
        return stats;
    }

    /**
     * Mark expired pending payments as expired
     */
    @Transactional
    public int markExpiredPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<BankTransferPayment> expiredPending = repository.findExpiredPending(now);

        for (BankTransferPayment payment : expiredPending) {
            payment.setStatus(BankTransferPayment.Status.EXPIRED);
            payment.setLastUpdatedAt(now);
            repository.persist(payment);
        }

        LOG.info("Marked {} Bank Transfer payments as expired", expiredPending.size());
        return expiredPending.size();
    }

    /**
     * Clean up old expired payments
     */
    @Transactional
    public long cleanupExpiredPayments(int daysOld) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);
        long deleted = repository.deleteExpiredOlderThan(threshold);
        LOG.info("Deleted {} expired Bank Transfer payments older than {} days", deleted, daysOld);
        return deleted;
    }

    /**
     * Get customer's Bank Transfer payment history
     */
    public List<BankTransferPayment> getCustomerPayments(String customerEmail) {
        return repository.findByCustomerEmail(customerEmail);
    }

    /**
     * Get payments by gateway provider
     */
    public List<BankTransferPayment> getPaymentsByProvider(String gatewayProvider) {
        return repository.findByGatewayProvider(gatewayProvider);
    }

    /**
     * Get payments by bank code
     */
    public List<BankTransferPayment> getPaymentsByBank(String bankCode) {
        return repository.findByBankCode(bankCode);
    }

    /**
     * Check if Bank Transfer payment method is available
     */
    public boolean isBankTransferAvailable() {
        return bankTransferConfig.isValidationEnabled();
    }

    /**
     * Get supported banks
     */
    public String[] getSupportedBanks() {
        String banks = bankTransferConfig.getSupportedBanks();
        return banks.split(",");
    }

    /**
     * Get VA prefix for a bank
     */
    public String getVAPrefix(String bankCode) {
        switch (bankCode.toUpperCase()) {
            case "BCA":
                return bankTransferConfig.getBCAVAPrefix();
            case "MANDIRI":
                return bankTransferConfig.getMandiriVAPrefix();
            case "BNI":
                return bankTransferConfig.getBNIVAPrefix();
            case "BRI":
                return bankTransferConfig.getBRIVAPrefix();
            case "PERMATA":
                return bankTransferConfig.getPermataVAPrefix();
            default:
                return "";
        }
    }

    /**
     * Statistics data class for Bank Transfer payments
     */
    public static class BankTransferPaymentStats {
        private String bankCode;
        private long totalPayments;
        private long pendingPayments;
        private long successfulPayments;
        private long failedPayments;
        private long expiredPayments;
        private long cancelledPayments;
        private long refundedPayments;

        public String getBankCode() {
            return bankCode;
        }

        public void setBankCode(String bankCode) {
            this.bankCode = bankCode;
        }

        public long getTotalPayments() {
            return totalPayments;
        }

        public void setTotalPayments(long totalPayments) {
            this.totalPayments = totalPayments;
        }

        public long getPendingPayments() {
            return pendingPayments;
        }

        public void setPendingPayments(long pendingPayments) {
            this.pendingPayments = pendingPayments;
        }

        public long getSuccessfulPayments() {
            return successfulPayments;
        }

        public void setSuccessfulPayments(long successfulPayments) {
            this.successfulPayments = successfulPayments;
        }

        public long getFailedPayments() {
            return failedPayments;
        }

        public void setFailedPayments(long failedPayments) {
            this.failedPayments = failedPayments;
        }

        public long getExpiredPayments() {
            return expiredPayments;
        }

        public void setExpiredPayments(long expiredPayments) {
            this.expiredPayments = expiredPayments;
        }

        public long getCancelledPayments() {
            return cancelledPayments;
        }

        public void setCancelledPayments(long cancelledPayments) {
            this.cancelledPayments = cancelledPayments;
        }

        public long getRefundedPayments() {
            return refundedPayments;
        }

        public void setRefundedPayments(long refundedPayments) {
            this.refundedPayments = refundedPayments;
        }

        public double getSuccessRate() {
            if (totalPayments == 0) {
                return 0.0;
            }
            return (double) successfulPayments / totalPayments * 100;
        }
    }
}
