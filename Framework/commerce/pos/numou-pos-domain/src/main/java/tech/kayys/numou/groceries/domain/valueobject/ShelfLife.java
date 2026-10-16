package tech.kayys.syirkah.groceries.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Shelf life value object for perishable goods.
 */
public final class ShelfLife implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final Instant productionDate;
    private final Instant expiryDate;
    private final int shelfLifeDays;

    public ShelfLife(Instant productionDate, Instant expiryDate) {
        this.productionDate = productionDate;
        this.expiryDate = expiryDate;
        this.shelfLifeDays = (int) ChronoUnit.DAYS.between(productionDate, expiryDate);
        validate();
    }

    @Override
    public void validate() {
        if (productionDate == null) {
            throw new IllegalArgumentException("Production date cannot be null");
        }
        if (expiryDate == null) {
            throw new IllegalArgumentException("Expiry date cannot be null");
        }
        if (expiryDate.isBefore(productionDate)) {
            throw new IllegalArgumentException("Expiry date must be after production date");
        }
        if (shelfLifeDays <= 0) {
            throw new IllegalArgumentException("Shelf life must be positive");
        }
    }

    public Instant getProductionDate() { return productionDate; }
    public Instant getExpiryDate() { return expiryDate; }
    public int getShelfLifeDays() { return shelfLifeDays; }

    public boolean isExpired(Instant currentDate) {
        return currentDate.isAfter(expiryDate);
    }

    public boolean isExpiringSoon(int daysThreshold) {
        Instant threshold = Instant.now().plusSeconds(daysThreshold * 24L * 60L * 60L);
        return expiryDate.isBefore(threshold);
    }

    public int getDaysRemaining() {
        return (int) ChronoUnit.DAYS.between(Instant.now(), expiryDate);
    }

    public int getDaysSinceProduction() {
        return (int) ChronoUnit.DAYS.between(productionDate, Instant.now());
    }

    public double getLifeUsedPercentage() {
        if (shelfLifeDays == 0) {
            return 0.0;
        }
        return (double) getDaysSinceProduction() / shelfLifeDays * 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShelfLife shelfLife = (ShelfLife) o;
        return Objects.equals(productionDate, shelfLife.productionDate) &&
               Objects.equals(expiryDate, shelfLife.expiryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productionDate, expiryDate);
    }

    @Override
    public String toString() {
        return "ShelfLife{" +
                "productionDate=" + productionDate +
                ", expiryDate=" + expiryDate +
                ", shelfLifeDays=" + shelfLifeDays +
                '}';
    }

    public static ShelfLife of(Instant productionDate, Instant expiryDate) {
        return new ShelfLife(productionDate, expiryDate);
    }

    public static ShelfLife of(Instant productionDate, int shelfLifeDays) {
        Instant expiryDate = productionDate.plusSeconds(shelfLifeDays * 24L * 60L * 60L);
        return new ShelfLife(productionDate, expiryDate);
    }
}
