package tech.kayys.numou.automotiveparts.domain;

import tech.kayys.numou.product.domain.product.ProductId;
import java.util.List;
import java.util.Objects;

/** Automotive semantics attached to an existing canonical Product. */
public record AutomotivePartProfile(String tenantId, ProductId productId, PartKind kind,
                                    String description, List<PartReference> references) {
    public AutomotivePartProfile {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        tenantId = tenantId.trim();
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(kind, "kind is required");
        description = description == null ? "" : description.trim();
        references = references == null ? List.of() : List.copyOf(references);
        if (references.stream().distinct().count() != references.size()) {
            throw new IllegalArgumentException("Duplicate part references are not allowed");
        }
    }

    public enum PartKind { REPLACEMENT_PART, CONSUMABLE, FLUID, ACCESSORY, ASSEMBLY, TOOL }
}