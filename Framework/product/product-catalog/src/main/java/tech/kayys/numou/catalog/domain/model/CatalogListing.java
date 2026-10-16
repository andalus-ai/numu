package tech.kayys.numou.catalog.domain.model;

import tech.kayys.numou.catalog.domain.valueobject.CatalogId;
import tech.kayys.numou.catalog.domain.valueobject.CatalogListingId;
import tech.kayys.numou.catalog.domain.valueobject.CatalogListingStatus;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/**
 * Catalog listing aggregate referencing canonical ProductId (config03.md §P4-17 #8).
 * Manages catalog-specific placement, overrides, and publication without duplicating
 * price or inventory state.
 */
public final class CatalogListing extends AbstractAggregateRoot<CatalogListingId> {

    private final CatalogId catalogId;
    private final ProductId productId;
    private CatalogListingStatus status;
    private int displayOrder;
    private String titleOverride;
    private String descriptionOverride;

    public CatalogListing(
            CatalogListingId id,
            CatalogId catalogId,
            ProductId productId,
            CatalogListingStatus status,
            int displayOrder
    ) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.catalogId = Objects.requireNonNull(catalogId, "catalogId cannot be null");
        this.productId = Objects.requireNonNull(productId, "productId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.displayOrder = displayOrder;
    }

    public static CatalogListing create(
            CatalogListingId id,
            CatalogId catalogId,
            ProductId productId,
            int displayOrder
    ) {
        return new CatalogListing(id, catalogId, productId, CatalogListingStatus.DRAFT, displayOrder);
    }

    public void publish() {
        this.status = CatalogListingStatus.PUBLISHED;
    }

    public void unpublish() {
        this.status = CatalogListingStatus.UNPUBLISHED;
    }

    public void archive() {
        this.status = CatalogListingStatus.ARCHIVED;
    }

    public void setOverrides(String titleOverride, String descriptionOverride) {
        this.titleOverride = titleOverride;
        this.descriptionOverride = descriptionOverride;
    }

    public CatalogId getCatalogId() {
        return catalogId;
    }

    public ProductId getProductId() {
        return productId;
    }

    public CatalogListingStatus getStatus() {
        return status;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getTitleOverride() {
        return titleOverride;
    }

    public String getDescriptionOverride() {
        return descriptionOverride;
    }
}
