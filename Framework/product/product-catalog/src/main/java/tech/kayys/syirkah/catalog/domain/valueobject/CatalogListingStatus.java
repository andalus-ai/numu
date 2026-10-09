package tech.kayys.syirkah.catalog.domain.valueobject;

/**
 * Publication lifecycle status of a catalog listing (config03.md §P4-17 #8).
 */
public enum CatalogListingStatus {
    DRAFT,
    PUBLISHED,
    UNPUBLISHED,
    ARCHIVED
}
