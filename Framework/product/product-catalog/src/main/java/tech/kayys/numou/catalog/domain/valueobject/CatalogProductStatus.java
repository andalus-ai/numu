package tech.kayys.syirkah.catalog.domain.valueobject;

/**
 * Catalog (bounded-context) product status.
 *
 * <p>This is <b>not</b> the Product foundation lifecycle
 * ({@code tech.kayys.syirkah.product.domain.product.ProductStatus}:
 * DRAFT → ACTIVE → DISCONTINUED → ARCHIVED). The Catalog context
 * keeps its own reversible model (DRAFT/ACTIVE/INACTIVE/DISCONTINUED)
 * because catalog listing and Product foundation lifecycle are
 * deliberately decoupled (product00.md).
 *
 * @deprecated kept for source compatibility; migrate to
 *     {@code CatalogProductStatus} once the rename is complete.
 */
@Deprecated
public enum CatalogProductStatus {
    DRAFT("Draft - not yet published"),
    ACTIVE("Active - available for sale"),
    INACTIVE("Inactive - temporarily unavailable"),
    DISCONTINUED("Discontinued - no longer sold");

    private final String description;

    CatalogProductStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canActivate() {
        return this == DRAFT || this == INACTIVE;
    }

    public boolean canDeactivate() {
        return this == ACTIVE;
    }
}
