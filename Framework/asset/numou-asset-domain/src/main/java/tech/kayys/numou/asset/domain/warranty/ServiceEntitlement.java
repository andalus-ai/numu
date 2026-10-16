package tech.kayys.numou.asset.domain.warranty;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Transactional entitlement bucket (ASSET-23): consumption must be transactional, never
 * derived from the WO table. No overspend allowed.
 */
public final class ServiceEntitlement {

    private final UUID id;
    private final String entitlementCode;
    private BigDecimal allocatedQuantity;
    private BigDecimal consumedQuantity;

    public ServiceEntitlement(UUID id, String entitlementCode, BigDecimal allocatedQuantity, BigDecimal consumedQuantity) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(entitlementCode, "entitlementCode cannot be null");
        if (entitlementCode.isBlank()) throw new BusinessRuleViolation("entitlementCode cannot be blank");
        this.entitlementCode = entitlementCode;
        if (allocatedQuantity == null || allocatedQuantity.signum() < 0) {
            throw new BusinessRuleViolation("allocatedQuantity cannot be negative");
        }
        this.allocatedQuantity = allocatedQuantity;
        this.consumedQuantity = consumedQuantity == null ? BigDecimal.ZERO : consumedQuantity;
        if (this.consumedQuantity.signum() < 0 || this.consumedQuantity.compareTo(this.allocatedQuantity) > 0) {
            throw new BusinessRuleViolation("consumedQuantity out of range");
        }
    }

    public void consume(BigDecimal quantity) {
        Objects.requireNonNull(quantity, "quantity cannot be null");
        if (quantity.signum() <= 0) {
            throw new BusinessRuleViolation("consume quantity must be positive");
        }
        if (remaining().compareTo(quantity) < 0) {
            throw new BusinessRuleViolation("entitlement " + entitlementCode + " has insufficient balance");
        }
        consumedQuantity = consumedQuantity.add(quantity);
    }

    public BigDecimal remaining() { return allocatedQuantity.subtract(consumedQuantity); }

    public UUID id() { return id; }
    public String entitlementCode() { return entitlementCode; }
    public BigDecimal allocatedQuantity() { return allocatedQuantity; }
    public BigDecimal consumedQuantity() { return consumedQuantity; }
}
