package tech.kayys.payment.domain;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant extends PanacheEntityBase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    
    @Column(name = "merchant_id", unique = true, nullable = false)
    public String merchantId;
    
    @Column(name = "name", nullable = false)
    public String name;
    
    @Column(name = "email", nullable = false)
    public String email;
    
    @Column(name = "phone")
    public String phone;
    
    @Column(name = "address")
    public String address;
    
    @Column(name = "business_type")
    public String businessType;
    
    @Column(name = "website")
    public String website;
    
    @Column(name = "api_key", nullable = false)
    public String apiKey;
    
    @Column(name = "secret_key", nullable = false)
    public String secretKey;
    
    @Column(name = "webhook_url")
    public String webhookUrl;
    
    @Column(name = "is_active", nullable = false)
    public Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}