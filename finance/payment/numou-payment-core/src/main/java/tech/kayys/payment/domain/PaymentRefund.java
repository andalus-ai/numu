package tech.kayys.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;

/**
 * Payment refund entity
 */
@Entity
public class PaymentRefund extends PanacheEntity {
    
    @ManyToOne
    public Payment payment;
    
    @Column(unique = true)
    public String refundId;
    
    @Column(nullable = false)
    public BigDecimal amount;
    
    @Column(nullable = false)
    public String reason;
    
    public String gatewayRefundId;
    
    public String gatewayProvider;
    
    public LocalDateTime refundDate;
    
    public RefundStatus status = RefundStatus.PENDING;
    
    public String errorMessage;
    
    public String notes;
    
    public enum RefundStatus {
        PENDING("Pending"),
        PROCESSING("Processing"),
        COMPLETED("Completed"),
        FAILED("Failed"),
        CANCELLED("Cancelled");
        
        private final String displayName;
        
        RefundStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    @PrePersist
    void prePersist() {
        if (this.refundDate == null) {
            this.refundDate = LocalDateTime.now();
        }
        if (this.refundId == null) {
            this.refundId = "REF" + System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        }
    }
}
