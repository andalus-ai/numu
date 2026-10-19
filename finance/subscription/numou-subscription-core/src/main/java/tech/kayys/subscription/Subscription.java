package tech.kayys.billing.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
class Subscription extends PanacheEntity {
    @ManyToOne
    public BillingAccount billingAccount;
    
    @ManyToOne
    public Product product;
    
    public LocalDate startDate;
    public LocalDate endDate;
    public int quantity = 1;
    public BigDecimal negotiatedPrice;
    public BillingAccount.BillingCycle billingCycle;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    
    public enum SubscriptionStatus {
        ACTIVE, CANCELED, SUSPENDED, EXPIRED
    }
    
    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.startDate == null) {
            this.startDate = LocalDate.now();
        }
        if (this.billingCycle == null && this.billingAccount != null) {
            this.billingCycle = this.billingAccount.billingCycle;
        }
    }
    
    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getEffectivePrice() {
        return negotiatedPrice != null ? negotiatedPrice : product.price;
    }
    
    public BigDecimal calculateAmount() {
        return getEffectivePrice().multiply(BigDecimal.valueOf(quantity));
    }
    
    public BigDecimal calculateTax() {
        if (product.taxable) {
            return calculateAmount().multiply(product.taxRate);
        }
        return BigDecimal.ZERO;
    }
    
    public BigDecimal calculateTotal() {
        return calculateAmount().add(calculateTax());
    }
}