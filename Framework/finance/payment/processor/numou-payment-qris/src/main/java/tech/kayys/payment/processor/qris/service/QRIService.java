package tech.kayys.payment.processor.qris.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.processor.qris.config.QRISConfig;
import tech.kayys.payment.processor.qris.domain.QRISPayment;
import tech.kayys.payment.processor.qris.repository.QRISPaymentRepository;

/**
 * Service class for QRIS payment operations and business logic.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class QRIService {

    private static final Logger LOG = LoggerFactory.getLogger(QRIService.class);

    @Inject
    QRISPaymentRepository repository;

    @Inject
    QRISConfig qrisConfig;

    /**
     * Get QRIS payment by transaction ID
     */
    public Optional<QRISPayment> getPayment(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    /**
     * Get QRIS payment by external order ID
     */
    public Optional<QRISPayment> getPaymentByOrderId(String externalOrderId) {
        return repository.findByExternalOrderId(externalOrderId);
    }

    /**
     * Get all pending QRIS payments
     */
    public List<QRISPayment> getPendingPayments() {
        return repository.findPending();
    }

    /**
     * Get all successful QRIS payments
     */
    public List<QRISPayment> getSuccessfulPayments() {
        return repository.findSuccessful();
    }

    /**
     * Get QRIS payments by status
     */
    public List<QRISPayment> getPaymentsByStatus(QRISPayment.Status status) {
        return repository.findByStatus(status);
    }

    /**
     * Get QRIS payments by payment method variant
     */
    public List<QRISPayment> getPaymentsByMethod(PaymentMethodType methodType) {
        return repository.findByPaymentMethod(methodType.name());
    }

    /**
     * Get QRIS payments within a date range
     */
    public List<QRISPayment> getPaymentsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByDateRange(startDate, endDate);
    }

    /**
     * Get today's QRIS payments
     */
    public List<QRISPayment> getTodayPayments() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return repository.findByDateRange(startOfDay, endOfDay);
    }

    /**
     * Get statistics for QRIS payments
     */
    public QRISPaymentStats getStats() {
        QRISPaymentStats stats = new QRISPaymentStats();
        stats.setTotalPayments(repository.count());
        stats.setPendingPayments(repository.countByStatus(QRISPayment.Status.PENDING));
        stats.setSuccessfulPayments(repository.countByStatus(QRISPayment.Status.SUCCESS));
        stats.setFailedPayments(repository.countByStatus(QRISPayment.Status.FAILED));
        stats.setExpiredPayments(repository.countByStatus(QRISPayment.Status.EXPIRED));
        stats.setCancelledPayments(repository.countByStatus(QRISPayment.Status.CANCELLED));
        stats.setRefundedPayments(repository.countByStatus(QRISPayment.Status.REFUNDED));
        return stats;
    }

    /**
     * Mark expired pending payments as expired
     */
    @Transactional
    public int markExpiredPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<QRISPayment> expiredPending = repository.findExpiredPending(now);

        for (QRISPayment payment : expiredPending) {
            payment.setStatus(QRISPayment.Status.EXPIRED);
            payment.setLastUpdatedAt(now);
            repository.persist(payment);
        }

        LOG.info("Marked {} QRIS payments as expired", expiredPending.size());
        return expiredPending.size();
    }

    /**
     * Clean up old expired payments
     */
    @Transactional
    public long cleanupExpiredPayments(int daysOld) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);
        long deleted = repository.deleteExpiredOlderThan(threshold);
        LOG.info("Deleted {} expired QRIS payments older than {} days", deleted, daysOld);
        return deleted;
    }

    /**
     * Get customer's QRIS payment history
     */
    public List<QRISPayment> getCustomerPayments(String customerEmail) {
        return repository.findByCustomerEmail(customerEmail);
    }

    /**
     * Get payments by gateway provider
     */
    public List<QRISPayment> getPaymentsByProvider(String gatewayProvider) {
        return repository.findByGatewayProvider(gatewayProvider);
    }

    /**
     * Check if QRIS payment method is available
     */
    public boolean isQRISAvailable() {
        return qrisConfig.isValidationEnabled();
    }

    /**
     * Get supported QRIS channels
     */
    public String[] getSupportedChannels() {
        String channels = qrisConfig.getSupportedChannels();
        return channels.split(",");
    }

    /**
     * Statistics data class for QRIS payments
     */
    public static class QRISPaymentStats {
        private long totalPayments;
        private long pendingPayments;
        private long successfulPayments;
        private long failedPayments;
        private long expiredPayments;
        private long cancelledPayments;
        private long refundedPayments;

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
