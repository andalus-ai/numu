package tech.kayys.billing.subscription;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/**
 * Sellable product a subscription points at. Price fields feed
 * {@link Subscription#calculateTotal()}; taxRate is a fraction
 * (e.g. 0.11 for 11% PPN).
 */
@Entity
class Product extends PanacheEntity {

    public String name;
    public BigDecimal price = BigDecimal.ZERO;
    public boolean taxable;
    public BigDecimal taxRate = BigDecimal.ZERO;
}
