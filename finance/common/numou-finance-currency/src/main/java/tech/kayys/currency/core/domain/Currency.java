package tech.kayys.sy.currency.core.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "currencies")
public class Currency extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true, length = 3)
    public String code; // ISO 4217 or custom e.g. "USD", "IDR", "PTS"

    @Column(nullable = false)
    public String name; // e.g. "US Dollar", "Indonesian Rupiah"

    @Column(nullable = false, length = 5)
    public String symbol; // e.g. "$", "Rp", "€"

    @Column(nullable = false)
    public int decimalPlaces; // e.g. 2 for USD, 0 for JPY

    @Column(nullable = false)
    public boolean active = true;

    @Version
    public Long version;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp  
    public Instant updatedAt;

    // Getters and setters...
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public int getDecimalPlaces() { return decimalPlaces; }
    public void setDecimalPlaces(int decimalPlaces) { this.decimalPlaces = decimalPlaces; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}