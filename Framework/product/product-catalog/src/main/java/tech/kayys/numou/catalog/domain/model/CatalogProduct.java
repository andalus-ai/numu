package tech.kayys.syirkah.catalog.domain.model;

import tech.kayys.syirkah.catalog.domain.event.ProductCreated;
import tech.kayys.syirkah.catalog.domain.event.ProductPriceChanged;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.catalog.domain.valueobject.CatalogProductStatus;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;

/**
 * Catalog (bounded-context) product aggregate.
 *
 * <p>This is <b>not</b> the Product foundation aggregate
 * ({@code tech.kayys.syirkah.product.domain.product.Product}).
 * The Catalog context adds catalog-listing concerns (price, stock
 * level, catalog status) that the Product foundation deliberately
 * does not know about (product00.md: no pricing/inventory inside
 * Product). It references the foundation by
 * {@code tech.kayys.syirkah.product.domain.product.ProductId}.
 *
 * @deprecated kept for source compatibility; migrate to
 *     {@code CatalogProduct} once the rename is complete.
 */
@Deprecated
public final class CatalogProduct extends AbstractAggregateRoot<ProductId> {

    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
    private Money price;
    private CatalogProductStatus status;
    private String sku;
    private int stockLevel;
    private boolean active;

    private CatalogProduct(ProductId id) {
        super(id);
        this.status = CatalogProductStatus.DRAFT;
        this.active = true;
        this.stockLevel = 0;
    }

    // Private constructor for ORM/deserialization
    private CatalogProduct() {
        super();
    }

    /**
     * Factory method to create a new Catalog product.
     * This is the only way to create a Catalog product, ensuring business invariants.
     */
    public static CatalogProduct create(
            ProductId id,
            String name,
            String description,
            Money price,
            String sku
    ) {
        CatalogProduct product = new CatalogProduct(id);
        product.name = name;
        product.description = description;
        product.price = price;
        product.sku = sku;
        product.status = CatalogProductStatus.DRAFT;
        product.active = true;
        product.stockLevel = 0;

        // Register domain event
        product.registerEvent(new ProductCreated(product));

        return product;
    }

    /**
     * Business method: Activate the product for sale.
     */
    public void activate() {
        if (this.status == CatalogProductStatus.ACTIVE) {
            return;
        }
        if (price == null || price.getAmount().signum() <= 0) {
            throw new IllegalStateException("Cannot activate product without valid price");
        }
        this.status = CatalogProductStatus.ACTIVE;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Business method: Deactivate the product.
     */
    public void deactivate() {
        this.status = CatalogProductStatus.INACTIVE;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Business method: Update the product's price.
     */
    public void changePrice(Money newPrice) {
        if (newPrice == null || newPrice.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }

        Money oldPrice = this.price;
        this.price = newPrice;
        setUpdatedAt(Instant.now());
        incrementVersion();

        // Register domain event
        registerEvent(new ProductPriceChanged(this, oldPrice, newPrice));
    }

    /**
     * Business method: Update stock level.
     */
    public void adjustStock(int quantity) {
        if (this.stockLevel + quantity < 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }
        this.stockLevel += quantity;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Money getPrice() { return price; }
    public CatalogProductStatus getStatus() { return status; }
    public String getSku() { return sku; }
    public int getStockLevel() { return stockLevel; }
    public boolean isActive() { return active && status == CatalogProductStatus.ACTIVE; }

    /**
     * Checks if the product is available for sale.
     */
    public boolean isAvailable() {
        return isActive() && stockLevel > 0;
    }

    @Override
    public String toString() {
        return "CatalogProduct{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", sku='" + sku + '\'' +
                ", price=" + price +
                ", status=" + status +
                '}';
    }
}
