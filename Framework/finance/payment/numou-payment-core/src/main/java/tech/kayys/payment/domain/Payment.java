package tech.kayys.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Payment entity representing a payment transaction
 */
@Entity
public class Payment extends PanacheEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    public Invoice invoice;

    @Column(unique = true, nullable = false)
    public String transactionId;

    public LocalDateTime paymentDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PaymentMethodType paymentMethod;
    
    @Column(nullable = false)
    public BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PaymentStatus status = PaymentStatus.PENDING;
    
    public String externalReference;
    public String externalOrderId;
    
    @Column(name = "gateway_provider")
    public String gatewayProvider;
    
    public String gatewayTransactionId;
    
    public LocalDateTime expiryTime;
    
    public String paymentUrl;
    
    @Column(columnDefinition = "TEXT")
    public String qrCodeString;
    
    public String qrCodeUrl;
    
    public String virtualAccountNumber;
    
    public String bankCode;
    
    public String customerName;
    
    public String customerEmail;
    
    public String customerPhone;
    
    public String notes;
    
    @ElementCollection(fetch = FetchType.EAGER)
    public Map<String, String> metadata = new HashMap<>();
    
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public java.util.List<PaymentRefund> refunds;
    
    /**
     * Payment status enumeration
     */
    public enum PaymentStatus {
        PENDING("Pending"),
        PROCESSING("Processing"),
        COMPLETED("Completed"),
        SUCCESS("Success"),
        FAILED("Failed"),
        CANCELLED("Cancelled"),
        EXPIRED("Expired"),
        REFUNDED("Refunded"),
        PARTIALLY_REFUNDED("Partially Refunded"),
        CHALLENGE("Challenge");
        
        private final String displayName;
        
        PaymentStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public boolean isFinal() {
            return this == COMPLETED || this == SUCCESS || this == FAILED || 
                   this == CANCELLED || this == EXPIRED || this == REFUNDED;
        }
        
        public boolean isSuccess() {
            return this == COMPLETED || this == SUCCESS;
        }
    }

    @PrePersist
    void prePersist() {
        if (this.paymentDate == null) {
            this.paymentDate = LocalDateTime.now();
        }
        if (this.transactionId == null) {
            this.transactionId = "TXN" + System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        }
    }
    
    @PreUpdate
    void preUpdate() {
        // Update timestamp on modification
    }
    
    /**
     * Check if payment is completed successfully
     */
    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED || this.status == PaymentStatus.SUCCESS;
    }
    
    /**
     * Check if payment is pending
     */
    public boolean isPending() {
        return this.status == PaymentStatus.PENDING || this.status == PaymentStatus.PROCESSING;
    }
    
    /**
     * Check if payment can be refunded
     */
    public boolean canBeRefunded() {
        return isCompleted() && this.status != PaymentStatus.REFUNDED;
    }
    
    /**
     * Add metadata entry
     */
    public void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }
    
    /**
     * Get metadata entry
     */
    public String getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }
}
