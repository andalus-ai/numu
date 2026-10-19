package tech.kayys.payment.processor.bank.va;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity class representing a Virtual Account (VA).
 * 
 * Virtual Accounts are dedicated account numbers assigned to customers
 * for receiving payments. Each VA is linked to a specific bank and
 * can be either static (reusable) or dynamic (single-use).
 * 
 * @author Numou Platform
 */
@Entity
@Table(name = "virtual_accounts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bank_code", "va_number"})
})
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Virtual Account number
     */
    @Column(name = "va_number", length = 50, nullable = false)
    private String vaNumber;

    /**
     * Bank code (BCA, MANDIRI, BNI, BRI, PERMATA)
     */
    @Column(name = "bank_code", length = 20, nullable = false)
    private String bankCode;

    /**
     * Bank name
     */
    @Column(name = "bank_name", length = 100)
    private String bankName;

    /**
     * Account holder name
     */
    @Column(name = "account_holder_name", length = 255)
    private String accountHolderName;

    /**
     * Customer ID who owns this VA
     */
    @Column(name = "customer_id", length = 100)
    private String customerId;

    /**
     * Customer email
     */
    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    /**
     * Customer phone
     */
    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    /**
     * VA Type: STATIC (reusable) or DYNAMIC (single-use)
     */
    @Column(name = "va_type", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private VAType vaType = VAType.DYNAMIC;

    /**
     * VA Status
     */
    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private VAStatus status = VAStatus.ACTIVE;

    /**
     * Fixed amount for fixed-amount VA (null for variable amount)
     */
    @Column(name = "fixed_amount", precision = 19, scale = 2)
    private BigDecimal fixedAmount;

    /**
     * Minimum amount that can be paid
     */
    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    /**
     * Maximum amount that can be paid
     */
    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    /**
     * Total amount paid to this VA
     */
    @Column(name = "total_paid", precision = 19, scale = 2)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    /**
     * Number of successful payments
     */
    @Column(name = "payment_count")
    private Integer paymentCount = 0;

    /**
     * External VA ID from gateway provider
     */
    @Column(name = "external_va_id", length = 100)
    private String externalVaId;

    /**
     * Gateway provider (MIDTRANS, XENDIT, etc.)
     */
    @Column(name = "gateway_provider", length = 50)
    private String gatewayProvider;

    /**
     * Expiry date/time for the VA
     */
    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    /**
     * Whether the VA has expired
     */
    @Column(name = "is_expired")
    private boolean expired = false;

    /**
     * Date when VA was activated
     */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    /**
     * Date when VA was deactivated
     */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /**
     * Reason for deactivation
     */
    @Column(name = "deactivation_reason", columnDefinition = "TEXT")
    private String deactivationReason;

    /**
     * Additional metadata in JSON format
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    public enum VAType {
        STATIC("Static - Reusable"),
        DYNAMIC("Dynamic - Single Use");

        private final String displayName;

        VAType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum VAStatus {
        ACTIVE("Active"),
        INACTIVE("Inactive"),
        EXPIRED("Expired"),
        SUSPENDED("Suspended"),
        CLOSED("Closed");

        private final String displayName;

        VAStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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

    public VAType getVaType() {
        return vaType;
    }

    public void setVaType(VAType vaType) {
        this.vaType = vaType;
    }

    public VAStatus getStatus() {
        return status;
    }

    public void setStatus(VAStatus status) {
        this.status = status;
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

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid;
    }

    public Integer getPaymentCount() {
        return paymentCount;
    }

    public void setPaymentCount(Integer paymentCount) {
        this.paymentCount = paymentCount;
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

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public LocalDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(LocalDateTime deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public String getDeactivationReason() {
        return deactivationReason;
    }

    public void setDeactivationReason(String deactivationReason) {
        this.deactivationReason = deactivationReason;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    /**
     * Check if VA is currently active
     */
    public boolean isActive() {
        return status == VAStatus.ACTIVE && !expired;
    }

    /**
     * Check if VA can accept payments
     */
    public boolean canAcceptPayment() {
        if (!isActive()) {
            return false;
        }
        
        if (vaType == VAType.DYNAMIC && paymentCount > 0) {
            return false; // Dynamic VA already used
        }
        
        if (expiryTime != null && LocalDateTime.now().isAfter(expiryTime)) {
            return false;
        }
        
        return true;
    }

    /**
     * Record a successful payment
     */
    public void recordPayment(BigDecimal amount) {
        if (totalPaid == null) {
            totalPaid = BigDecimal.ZERO;
        }
        totalPaid = totalPaid.add(amount);
        
        if (paymentCount == null) {
            paymentCount = 0;
        }
        paymentCount++;
        
        // For dynamic VA, mark as used after first payment
        if (vaType == VAType.DYNAMIC && paymentCount >= 1) {
            status = VAStatus.INACTIVE;
        }
    }

    /**
     * Check if amount is within allowed range
     */
    public boolean isValidAmount(BigDecimal amount) {
        if (fixedAmount != null) {
            return amount.compareTo(fixedAmount) == 0;
        }
        
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            return false;
        }
        
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            return false;
        }
        
        return true;
    }

    @Override
    public String toString() {
        return "VirtualAccount{" +
               "id=" + id +
               ", vaNumber='" + vaNumber + '\'' +
               ", bankCode='" + bankCode + '\'' +
               ", vaType=" + vaType +
               ", status=" + status +
               '}';
    }
}
