package tech.kayys.sy.currency.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "exchange_rates", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"from_currency", "to_currency", "effective_date"}))
public class ExchangeRate extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_currency", nullable = false)
    public Currency fromCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_currency", nullable = false)
    public Currency toCurrency;

    @Column(nullable = false, precision = 19, scale = 8)
    public BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    public LocalDate effectiveDate;

    @Column(name = "expiry_date")
    public LocalDate expiryDate;

    @Column(nullable = false)
    public boolean active = true;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
}