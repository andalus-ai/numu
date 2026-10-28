package tech.kayys.numou.automotiveparts.domain;

import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/** Associates a positive fitment claim with the canonical product it qualifies. */
public record PartFitment(String tenantId, ProductId productId, VehicleFitment fitment) {
    public PartFitment {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        tenantId = tenantId.trim();
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(fitment, "fitment is required");
        if (!tenantId.equals(fitment.tenantId())) {
            throw new IllegalArgumentException("Part fitment and vehicle fitment must belong to the same tenant");
        }
    }
}