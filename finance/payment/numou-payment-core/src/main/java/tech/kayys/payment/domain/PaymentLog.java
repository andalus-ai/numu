package tech.kayys.payment.domain;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_logs")
public class PaymentLog extends PanacheEntityBase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    
    @Column(name = "transaction_id", nullable = false)
    public String transactionId;
    
    @Column(name = "event_type", nullable = false)
    public String eventType;
    
    @Column(name = "old_status")
    public String oldStatus;
    
    @Column(name = "new_status")
    public String newStatus;
    
    @Column(name = "description")
    public String description;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    public String metadata;
    
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}