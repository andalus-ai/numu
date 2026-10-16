package tech.kayys.payment.domain;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import tech.kayys.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund extends PanacheEntityBase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    
    @Column(name = "refund_id", unique = true, nullable = false)
    public String refundId;
    
    @Column(name = "transaction_id", nullable = false)
    public String transactionId;
    
    @Column(name = "merchant_id", nullable = false)
    public String merchantId;
    
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    public BigDecimal amount;
    
    @Column(name = "reason")
    public String reason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public PaymentStatus status = PaymentStatus.PENDING;
    
    @Column(name = "gateway_response", columnDefinition = "TEXT")
    public String gatewayResponse;
    
    @Column(name = "processed_at")
    public LocalDateTime processedAt;
    
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
