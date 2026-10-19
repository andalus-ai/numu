package tech.kayys.payment.processor.bank.va;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.payment.processor.bank.config.BankTransferConfig;

/**
 * Service class for Virtual Account lifecycle management.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class VirtualAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualAccountService.class);

    @Inject
    VirtualAccountRepository repository;

    @Inject
    VAGenerator vaGenerator;

    @Inject
    BankTransferConfig bankTransferConfig;

    /**
     * Create a new Virtual Account
     */
    @Transactional
    public VirtualAccount createVA(CreateVARequest request) {
        String bankCode = request.getBankCode();
        String vaNumber = vaGenerator.generate(bankCode, request.getCustomerId(), request.getTransactionId());

        VirtualAccount va = new VirtualAccount();
        va.setVaNumber(vaNumber);
        va.setBankCode(bankCode);
        va.setBankName(getBankName(bankCode));
        va.setAccountHolderName(request.getAccountHolderName());
        va.setCustomerId(request.getCustomerId());
        va.setCustomerEmail(request.getCustomerEmail());
        va.setCustomerPhone(request.getCustomerPhone());
        va.setVaType(request.getVaType() != null ? request.getVaType() : VirtualAccount.VAType.DYNAMIC);
        va.setStatus(VirtualAccount.VAStatus.ACTIVE);
        va.setFixedAmount(request.getFixedAmount());
        va.setMinAmount(request.getMinAmount());
        va.setMaxAmount(request.getMaxAmount());
        va.setExternalVaId(request.getExternalVaId());
        va.setGatewayProvider(request.getGatewayProvider());
        va.setExpiryTime(request.getExpiryTime());
        va.setMetadata(request.getMetadata());
        va.setCreatedAt(LocalDateTime.now());
        va.setActivatedAt(LocalDateTime.now());

        repository.persist(va);

        LOG.info("Created Virtual Account: {} for bank: {}, customer: {}", 
                vaNumber, bankCode, request.getCustomerId());

        return va;
    }

    /**
     * Create a static (reusable) Virtual Account
     */
    @Transactional
    public VirtualAccount createStaticVA(String bankCode, String customerId, String accountHolderName) {
        CreateVARequest request = new CreateVARequest();
        request.setBankCode(bankCode);
        request.setCustomerId(customerId);
        request.setAccountHolderName(accountHolderName);
        request.setVaType(VirtualAccount.VAType.STATIC);

        return createVA(request);
    }

    /**
     * Create a dynamic (single-use) Virtual Account
     */
    @Transactional
    public VirtualAccount createDynamicVA(String bankCode, String transactionId, BigDecimal amount) {
        CreateVARequest request = new CreateVARequest();
        request.setBankCode(bankCode);
        request.setTransactionId(transactionId);
        request.setVaType(VirtualAccount.VAType.DYNAMIC);
        request.setFixedAmount(amount);
        
        // Set expiry time
        int expiryMinutes = bankTransferConfig.getExpiryMinutes();
        request.setExpiryTime(LocalDateTime.now().plusMinutes(expiryMinutes));

        return createVA(request);
    }

    /**
     * Get Virtual Account by VA number
     */
    public Optional<VirtualAccount> getVA(String vaNumber) {
        return repository.findByVaNumber(vaNumber);
    }

    /**
     * Get Virtual Account by VA number and bank code
     */
    public Optional<VirtualAccount> getVA(String vaNumber, String bankCode) {
        return repository.findByVaNumberAndBankCode(vaNumber, bankCode);
    }

    /**
     * Get customer's Virtual Accounts
     */
    public List<VirtualAccount> getCustomerVAs(String customerId) {
        return repository.findByCustomerId(customerId);
    }

    /**
     * Get customer's active Virtual Accounts
     */
    public List<VirtualAccount> getCustomerActiveVAs(String customerId) {
        return repository.findCustomerActiveAccounts(customerId);
    }

    /**
     * Get all active Virtual Accounts
     */
    public List<VirtualAccount> getActiveVAs() {
        return repository.findActive();
    }

    /**
     * Get active Virtual Accounts by bank
     */
    public List<VirtualAccount> getActiveVAsByBank(String bankCode) {
        return repository.findActiveByBank(bankCode);
    }

    /**
     * Get available Virtual Accounts (active and not expired)
     */
    public List<VirtualAccount> getAvailableVAs() {
        return repository.findAvailable();
    }

    /**
     * Get available Virtual Accounts for a specific bank
     */
    public List<VirtualAccount> getAvailableVAsByBank(String bankCode) {
        return repository.findAvailableByBank(bankCode);
    }

    /**
     * Check if VA can accept payment
     */
    public boolean canAcceptPayment(String vaNumber) {
        Optional<VirtualAccount> vaOpt = repository.findByVaNumber(vaNumber);
        return vaOpt.isPresent() && vaOpt.get().canAcceptPayment();
    }

    /**
     * Validate payment amount for VA
     */
    public boolean isValidPaymentAmount(String vaNumber, BigDecimal amount) {
        Optional<VirtualAccount> vaOpt = repository.findByVaNumber(vaNumber);
        if (!vaOpt.isPresent()) {
            return false;
        }
        return vaOpt.get().isValidAmount(amount);
    }

    /**
     * Record a successful payment to VA
     */
    @Transactional
    public VirtualAccount recordPayment(String vaNumber, BigDecimal amount, String transactionId) {
        Optional<VirtualAccount> vaOpt = repository.findByVaNumber(vaNumber);
        if (!vaOpt.isPresent()) {
            throw new IllegalArgumentException("Virtual Account not found: " + vaNumber);
        }

        VirtualAccount va = vaOpt.get();
        
        if (!va.canAcceptPayment()) {
            throw new IllegalStateException("Virtual Account cannot accept payment: " + vaNumber);
        }

        if (!va.isValidAmount(amount)) {
            throw new IllegalArgumentException("Invalid payment amount for VA: " + amount);
        }

        va.recordPayment(amount);
        va.setLastUpdatedAt(LocalDateTime.now());
        
        // Add transaction reference to metadata
        Map<String, String> metadata = getMetadataAsMap(va.getMetadata());
        metadata.put("last_transaction_id", transactionId);
        metadata.put("last_payment_time", LocalDateTime.now().toString());
        metadata.put("last_payment_amount", amount.toString());
        va.setMetadata(mapToJson(metadata));

        repository.persist(va);

        LOG.info("Recorded payment of {} to VA: {}, transaction: {}", 
                amount, vaNumber, transactionId);

        return va;
    }

    /**
     * Deactivate a Virtual Account
     */
    @Transactional
    public VirtualAccount deactivateVA(String vaNumber, String reason) {
        Optional<VirtualAccount> vaOpt = repository.findByVaNumber(vaNumber);
        if (!vaOpt.isPresent()) {
            throw new IllegalArgumentException("Virtual Account not found: " + vaNumber);
        }

        VirtualAccount va = vaOpt.get();
        va.setStatus(VirtualAccount.VAStatus.INACTIVE);
        va.setDeactivatedAt(LocalDateTime.now());
        va.setDeactivationReason(reason);
        va.setLastUpdatedAt(LocalDateTime.now());

        repository.persist(va);

        LOG.info("Deactivated Virtual Account: {}, reason: {}", vaNumber, reason);

        return va;
    }

    /**
     * Reactivate a Virtual Account
     */
    @Transactional
    public VirtualAccount reactivateVA(String vaNumber) {
        Optional<VirtualAccount> vaOpt = repository.findByVaNumber(vaNumber);
        if (!vaOpt.isPresent()) {
            throw new IllegalArgumentException("Virtual Account not found: " + vaNumber);
        }

        VirtualAccount va = vaOpt.get();
        
        if (va.isExpired()) {
            throw new IllegalStateException("Cannot reactivate expired VA: " + vaNumber);
        }

        va.setStatus(VirtualAccount.VAStatus.ACTIVE);
        va.setActivatedAt(LocalDateTime.now());
        va.setDeactivatedAt(null);
        va.setDeactivationReason(null);
        va.setLastUpdatedAt(LocalDateTime.now());

        repository.persist(va);

        LOG.info("Reactivated Virtual Account: {}", vaNumber);

        return va;
    }

    /**
     * Suspend a Virtual Account
     */
    @Transactional
    public VirtualAccount suspendVA(String vaNumber, String reason) {
        Optional<VirtualAccount> vaOpt = repository.findByVaNumber(vaNumber);
        if (!vaOpt.isPresent()) {
            throw new IllegalArgumentException("Virtual Account not found: " + vaNumber);
        }

        VirtualAccount va = vaOpt.get();
        va.setStatus(VirtualAccount.VAStatus.SUSPENDED);
        va.setDeactivationReason(reason);
        va.setLastUpdatedAt(LocalDateTime.now());

        repository.persist(va);

        LOG.info("Suspended Virtual Account: {}, reason: {}", vaNumber, reason);

        return va;
    }

    /**
     * Close a Virtual Account permanently
     */
    @Transactional
    public VirtualAccount closeVA(String vaNumber, String reason) {
        Optional<VirtualAccount> vaOpt = repository.findByVaNumber(vaNumber);
        if (!vaOpt.isPresent()) {
            throw new IllegalArgumentException("Virtual Account not found: " + vaNumber);
        }

        VirtualAccount va = vaOpt.get();
        va.setStatus(VirtualAccount.VAStatus.CLOSED);
        va.setDeactivatedAt(LocalDateTime.now());
        va.setDeactivationReason(reason);
        va.setLastUpdatedAt(LocalDateTime.now());

        repository.persist(va);

        LOG.info("Closed Virtual Account: {}, reason: {}", vaNumber, reason);

        return va;
    }

    /**
     * Mark expired Virtual Accounts
     */
    @Transactional
    public int markExpiredVAs() {
        LocalDateTime now = LocalDateTime.now();
        List<VirtualAccount> expiringVAs = repository.findExpiringBefore(now);

        for (VirtualAccount va : expiringVAs) {
            va.setExpired(true);
            va.setStatus(VirtualAccount.VAStatus.EXPIRED);
            va.setLastUpdatedAt(now);
            repository.persist(va);
        }

        LOG.info("Marked {} Virtual Accounts as expired", expiringVAs.size());
        return expiringVAs.size();
    }

    /**
     * Get VA statistics
     */
    public VAStats getStats() {
        VAStats stats = new VAStats();
        stats.setTotalVAs(repository.count());
        stats.setActiveVAs(repository.countByStatus(VirtualAccount.VAStatus.ACTIVE));
        stats.setInactiveVAs(repository.countByStatus(VirtualAccount.VAStatus.INACTIVE));
        stats.setExpiredVAs(repository.countByStatus(VirtualAccount.VAStatus.EXPIRED));
        stats.setSuspendedVAs(repository.countByStatus(VirtualAccount.VAStatus.SUSPENDED));
        stats.setClosedVAs(repository.countByStatus(VirtualAccount.VAStatus.CLOSED));
        stats.setStaticVAs(repository.countByVaType(VirtualAccount.VAType.STATIC));
        stats.setDynamicVAs(repository.countByVaType(VirtualAccount.VAType.DYNAMIC));
        return stats;
    }

    /**
     * Get VA statistics by bank
     */
    public VAStats getStatsByBank(String bankCode) {
        VAStats stats = new VAStats();
        stats.setBankCode(bankCode);
        stats.setTotalVAs(repository.countByBankCode(bankCode));
        stats.setActiveVAs(repository.countActiveByBankCode(bankCode));
        
        List<VirtualAccount> allVAs = repository.findByBankCode(bankCode);
        stats.setStaticVAs(allVAs.stream()
            .filter(va -> va.getVaType() == VirtualAccount.VAType.STATIC).count());
        stats.setDynamicVAs(allVAs.stream()
            .filter(va -> va.getVaType() == VirtualAccount.VAType.DYNAMIC).count());
        
        return stats;
    }

    /**
     * Cleanup old inactive VAs
     */
    @Transactional
    public long cleanupInactiveVAs(int daysOld) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);
        long deleted = repository.deleteInactiveOlderThan(threshold);
        LOG.info("Deleted {} inactive Virtual Accounts older than {} days", deleted, daysOld);
        return deleted;
    }

    /**
     * Cleanup old expired VAs
     */
    @Transactional
    public long cleanupExpiredVAs(int daysOld) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);
        long deleted = repository.deleteExpiredOlderThan(threshold);
        LOG.info("Deleted {} expired Virtual Accounts older than {} days", deleted, daysOld);
        return deleted;
    }

    private String getBankName(String bankCode) {
        switch (bankCode.toUpperCase()) {
            case "BCA": return "Bank Central Asia";
            case "MANDIRI": return "Bank Mandiri";
            case "BNI": return "Bank Negara Indonesia";
            case "BRI": return "Bank Rakyat Indonesia";
            case "PERMATA": return "Bank Permata";
            default: return bankCode;
        }
    }

    private Map<String, String> getMetadataAsMap(String metadata) {
        // Simple implementation - in production, use proper JSON parsing
        if (metadata == null || metadata.trim().isEmpty()) {
            return new HashMap<>();
        }
        // Placeholder - implement proper JSON parsing
        return new HashMap<>();
    }

    private String mapToJson(Map<String, String> map) {
        // Simple implementation - in production, use proper JSON serialization
        if (map == null || map.isEmpty()) {
            return null;
        }
        // Placeholder - implement proper JSON serialization
        return "{}";
    }

    /**
     * Request class for creating Virtual Accounts
     */
    public static class CreateVARequest {
        private String bankCode;
        private String customerId;
        private String transactionId;
        private String accountHolderName;
        private String customerEmail;
        private String customerPhone;
        private VirtualAccount.VAType vaType;
        private BigDecimal fixedAmount;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private String externalVaId;
        private String gatewayProvider;
        private LocalDateTime expiryTime;
        private String metadata;

        // Getters and Setters
        public String getBankCode() {
            return bankCode;
        }

        public void setBankCode(String bankCode) {
            this.bankCode = bankCode;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getAccountHolderName() {
            return accountHolderName;
        }

        public void setAccountHolderName(String accountHolderName) {
            this.accountHolderName = accountHolderName;
        }

        public String getCustomerEmail() {
            return customerEmail;
        }

        public void setCustomerEmail(String customerEmail) {
            this.customerEmail = customerEmail;
        }

        public String getCustomerPhone() {
            return customerPhone;
        }

        public void setCustomerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
        }

        public VirtualAccount.VAType getVaType() {
            return vaType;
        }

        public void setVaType(VirtualAccount.VAType vaType) {
            this.vaType = vaType;
        }

        public BigDecimal getFixedAmount() {
            return fixedAmount;
        }

        public void setFixedAmount(BigDecimal fixedAmount) {
            this.fixedAmount = fixedAmount;
        }

        public BigDecimal getMinAmount() {
            return minAmount;
        }

        public void setMinAmount(BigDecimal minAmount) {
            this.minAmount = minAmount;
        }

        public BigDecimal getMaxAmount() {
            return maxAmount;
        }

        public void setMaxAmount(BigDecimal maxAmount) {
            this.maxAmount = maxAmount;
        }

        public String getExternalVaId() {
            return externalVaId;
        }

        public void setExternalVaId(String externalVaId) {
            this.externalVaId = externalVaId;
        }

        public String getGatewayProvider() {
            return gatewayProvider;
        }

        public void setGatewayProvider(String gatewayProvider) {
            this.gatewayProvider = gatewayProvider;
        }

        public LocalDateTime getExpiryTime() {
            return expiryTime;
        }

        public void setExpiryTime(LocalDateTime expiryTime) {
            this.expiryTime = expiryTime;
        }

        public String getMetadata() {
            return metadata;
        }

        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * Statistics class for Virtual Accounts
     */
    public static class VAStats {
        private String bankCode;
        private long totalVAs;
        private long activeVAs;
        private long inactiveVAs;
        private long expiredVAs;
        private long suspendedVAs;
        private long closedVAs;
        private long staticVAs;
        private long dynamicVAs;

        public String getBankCode() {
            return bankCode;
        }

        public void setBankCode(String bankCode) {
            this.bankCode = bankCode;
        }

        public long getTotalVAs() {
            return totalVAs;
        }

        public void setTotalVAs(long totalVAs) {
            this.totalVAs = totalVAs;
        }

        public long getActiveVAs() {
            return activeVAs;
        }

        public void setActiveVAs(long activeVAs) {
            this.activeVAs = activeVAs;
        }

        public long getInactiveVAs() {
            return inactiveVAs;
        }

        public void setInactiveVAs(long inactiveVAs) {
            this.inactiveVAs = inactiveVAs;
        }

        public long getExpiredVAs() {
            return expiredVAs;
        }

        public void setExpiredVAs(long expiredVAs) {
            this.expiredVAs = expiredVAs;
        }

        public long getSuspendedVAs() {
            return suspendedVAs;
        }

        public void setSuspendedVAs(long suspendedVAs) {
            this.suspendedVAs = suspendedVAs;
        }

        public long getClosedVAs() {
            return closedVAs;
        }

        public void setClosedVAs(long closedVAs) {
            this.closedVAs = closedVAs;
        }

        public long getStaticVAs() {
            return staticVAs;
        }

        public void setStaticVAs(long staticVAs) {
            this.staticVAs = staticVAs;
        }

        public long getDynamicVAs() {
            return dynamicVAs;
        }

        public void setDynamicVAs(long dynamicVAs) {
            this.dynamicVAs = dynamicVAs;
        }
    }
}
