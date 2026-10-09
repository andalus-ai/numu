package tech.kayys.syirkah.catalog.domain.model;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.catalog.domain.valueobject.CatalogId;
import tech.kayys.syirkah.catalog.domain.valueobject.CatalogListingId;
import tech.kayys.syirkah.catalog.domain.valueobject.CatalogListingStatus;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CatalogListingTest {

    @Test
    void shouldCreateAndManageCatalogListingLifecycle() {
        CatalogListingId listingId = CatalogListingId.generate();
        CatalogId catalogId = CatalogId.generate();
        ProductId productId = ProductId.of(UUID.randomUUID());

        CatalogListing listing = CatalogListing.create(listingId, catalogId, productId, 10);
        assertEquals(CatalogListingStatus.DRAFT, listing.getStatus());
        assertEquals(10, listing.getDisplayOrder());
        assertEquals(catalogId, listing.getCatalogId());
        assertEquals(productId, listing.getProductId());

        listing.setOverrides("Custom Title", "Custom Description");
        assertEquals("Custom Title", listing.getTitleOverride());
        assertEquals("Custom Description", listing.getDescriptionOverride());

        listing.publish();
        assertEquals(CatalogListingStatus.PUBLISHED, listing.getStatus());

        listing.unpublish();
        assertEquals(CatalogListingStatus.UNPUBLISHED, listing.getStatus());

        listing.archive();
        assertEquals(CatalogListingStatus.ARCHIVED, listing.getStatus());
    }
}
