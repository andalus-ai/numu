package tech.kayys.payment.processor.bank.va;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.payment.processor.bank.config.BankTransferConfig;
import tech.kayys.payment.processor.bank.domain.BankTransferPayment;
import tech.kayys.payment.processor.bank.repository.BankTransferPaymentRepository;

/**
 * Handler for Virtual Account webhook notifications from banks/gateways.
 * 
 * Processes incoming payment notifications and updates VA and payment records.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class VAWebhookHandler {

    private static final Logger LOG = LoggerFactory.getLogger(VAWebhookHandler.class);

    @Inject
    VirtualAccountRepository vaRepository;

    @Inject
    BankTransferPaymentRepository paymentRepository;

    @Inject
    BankTransferConfig bankTransferConfig;

    /**
     * Handle incoming VA payment notification
     * 
     * @param notification Payment notification from bank/gateway
     * @return Processing result
     */
    @Transactional
    public VANotificationResult handlePaymentNotification(VANotification notification) {
        LOG.info("Processing VA payment notification: VA={}, Amount={}, Transaction={}",
                notification.getVaNumber(), notification.getAmount(), notification.getTransactionId());

        // Validate notification
        if (!validateNotification(notification)) {
            return VANotificationResult.error("Invalid notification", "INVALID_NOTIFICATION");
        }

        // Verify signature if configured
        if (bankTransferConfig.isWebhookEnabled() && !verifySignature(notification)) {
            return VANotificationResult.error("Invalid signature", "INVALID_SIGNATURE");
        }

        // Find Virtual Account
        Optional<VirtualAccount> vaOpt = vaRepository.findByVaNumber(notification.getVaNumber());
        if (!vaOpt.isPresent()) {
            LOG.warn("Virtual Account not found: {}", notification.getVaNumber());
            return VANotificationResult.error("VA not found", "VA_NOT_FOUND");
        }

        VirtualAccount va = vaOpt.get();

        // Check if VA can accept payment
        if (!va.canAcceptPayment()) {
            LOG.warn("Virtual Account cannot accept payment: {}, status={}", 
                    notification.getVaNumber(), va.getStatus());
            return VANotificationResult.error("VA cannot accept payment", "VA_NOT_ACCEPTING_PAYMENT");
        }

        // Validate payment amount
        if (!va.isValidAmount(notification.getAmount())) {
            LOG.warn("Invalid payment amount for VA {}: {}", notification.getVaNumber(), 
                    notification.getAmount());
            return VANotificationResult.error("Invalid payment amount", "INVALID_AMOUNT");
        }

        // Update Virtual Account
        va.recordPayment(notification.getAmount());
        va.setLastUpdatedAt(LocalDateTime.now());
        vaRepository.persist(va);

        // Find and update associated payment if transaction ID provided
        if (notification.getTransactionId() != null) {
            updatePaymentRecord(notification);
        }

        LOG.info("Successfully processed VA payment: VA={}, Amount={}", 
                notification.getVaNumber(), notification.getAmount());

        return VANotificationResult.success(va);
    }

    /**
     * Handle VA expiry notification
     */
    @Transactional
    public VANotificationResult handleExpiryNotification(String vaNumber, String externalVaId) {
        LOG.info("Processing VA expiry notification: VA={}", vaNumber);

        Optional<VirtualAccount> vaOpt;
        if (vaNumber != null) {
            vaOpt = vaRepository.findByVaNumber(vaNumber);
        } else if (externalVaId != null) {
            vaOpt = vaRepository.findByExternalVaId(externalVaId);
        } else {
            return VANotificationResult.error("VA identifier required", "MISSING_VA_IDENTIFIER");
        }

        if (!vaOpt.isPresent()) {
            return VANotificationResult.error("VA not found", "VA_NOT_FOUND");
        }

        VirtualAccount va = vaOpt.get();
        va.setExpired(true);
        va.setStatus(VirtualAccount.VAStatus.EXPIRED);
        va.setLastUpdatedAt(LocalDateTime.now());
        vaRepository.persist(va);

        LOG.info("Marked VA as expired: {}", vaNumber);

        return VANotificationResult.success(va);
    }

    /**
     * Handle VA closure notification from bank
     */
    @Transactional
    public VANotificationResult handleClosureNotification(String vaNumber, String reason) {
        LOG.info("Processing VA closure notification: VA={}, Reason={}", vaNumber, reason);

        Optional<VirtualAccount> vaOpt = vaRepository.findByVaNumber(vaNumber);
        if (!vaOpt.isPresent()) {
            return VANotificationResult.error("VA not found", "VA_NOT_FOUND");
        }

        VirtualAccount va = vaOpt.get();
        va.setStatus(VirtualAccount.VAStatus.CLOSED);
        va.setDeactivatedAt(LocalDateTime.now());
        va.setDeactivationReason(reason != null ? reason : "Closed by bank");
        va.setLastUpdatedAt(LocalDateTime.now());
        vaRepository.persist(va);

        LOG.info("Marked VA as closed: {}", vaNumber);

        return VANotificationResult.success(va);
    }

    /**
     * Validate notification data
     */
    private boolean validateNotification(VANotification notification) {
        if (notification.getVaNumber() == null || notification.getVaNumber().trim().isEmpty()) {
            return false;
        }
        
        if (notification.getAmount() == null || notification.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (notification.getPaymentTime() == null) {
            return false;
        }

        return true;
    }

    /**
     * Verify webhook signature
     */
    private boolean verifySignature(VANotification notification) {
        String expectedSignature = notification.getSignature();
        if (expectedSignature == null || expectedSignature.trim().isEmpty()) {
            return false;
        }

        String secret = bankTransferConfig.getWebhookSecret();
        if (secret == null || secret.trim().isEmpty()) {
            LOG.warn("Webhook secret not configured");
            return false;
        }

        // Implement signature verification based on gateway provider
        // This is a placeholder - implement actual signature verification
        // based on your gateway provider's specification
        
        LOG.debug("Verifying signature for notification");
        return true; // Placeholder
    }

    /**
     * Update associated payment record
     */
    private void updatePaymentRecord(VANotification notification) {
        Optional<BankTransferPayment> paymentOpt;
        
        if (notification.getTransactionId() != null) {
            paymentOpt = paymentRepository.findByTransactionId(notification.getTransactionId());
        } else if (notification.getExternalOrderId() != null) {
            paymentOpt = paymentRepository.findByExternalOrderId(notification.getExternalOrderId());
        } else {
            return;
        }

        if (!paymentOpt.isPresent()) {
            LOG.warn("Payment record not found for transaction: {}", notification.getTransactionId());
            return;
        }

        BankTransferPayment payment = paymentOpt.get();
        
        // Only update if payment is still pending
        if (payment.getStatus() != BankTransferPayment.Status.PENDING) {
            LOG.info("Payment already completed, skipping update: {}", payment.getTransactionId());
            return;
        }

        payment.setStatus(BankTransferPayment.Status.SUCCESS);
        payment.setPaidAt(notification.getPaymentTime());
        payment.setLastUpdatedAt(LocalDateTime.now());
        payment.setExternalTransactionId(notification.getExternalTransactionId());
        
        paymentRepository.persist(payment);

        LOG.info("Updated payment record to SUCCESS: {}", payment.getTransactionId());
    }

    /**
     * Notification data class
     */
    public static class VANotification {
        private String vaNumber;
        private String bankCode;
        private BigDecimal amount;
        private String transactionId;
        private String externalOrderId;
        private String externalTransactionId;
        private LocalDateTime paymentTime;
        private String signature;
        private String gatewayProvider;
        private String additionalInfo;

        // Getters and Setters
        public String getVaNumber() {
            return vaNumber;
        }

        public void setVaNumber(String vaNumber) {
            this.vaNumber = vaNumber;
        }

        public String getBankCode() {
            return bankCode;
        }

        public void setBankCode(String bankCode) {
            this.bankCode = bankCode;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getExternalOrderId() {
            return externalOrderId;
        }

        public void setExternalOrderId(String externalOrderId) {
            this.externalOrderId = externalOrderId;
        }

        public String getExternalTransactionId() {
            return externalTransactionId;
        }

        public void setExternalTransactionId(String externalTransactionId) {
            this.externalTransactionId = externalTransactionId;
        }

        public LocalDateTime getPaymentTime() {
            return paymentTime;
        }

        public void setPaymentTime(LocalDateTime paymentTime) {
            this.paymentTime = paymentTime;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getGatewayProvider() {
            return gatewayProvider;
        }

        public void setGatewayProvider(String gatewayProvider) {
            this.gatewayProvider = gatewayProvider;
        }

        public String getAdditionalInfo() {
            return additionalInfo;
        }

        public void setAdditionalInfo(String additionalInfo) {
            this.additionalInfo = additionalInfo;
        }
    }

    /**
     * Result class for notification processing
     */
    public static class VANotificationResult {
        private boolean success;
        private String errorMessage;
        private String errorCode;
        private VirtualAccount virtualAccount;

        public static VANotificationResult success(VirtualAccount va) {
            VANotificationResult result = new VANotificationResult();
            result.success = true;
            result.virtualAccount = va;
            return result;
        }

        public static VANotificationResult error(String message, String code) {
            VANotificationResult result = new VANotificationResult();
            result.success = false;
            result.errorMessage = message;
            result.errorCode = code;
            return result;
        }

        // Getters and Setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public VirtualAccount getVirtualAccount() {
            return virtualAccount;
        }

        public void setVirtualAccount(VirtualAccount virtualAccount) {
            this.virtualAccount = virtualAccount;
        }
    }
}
