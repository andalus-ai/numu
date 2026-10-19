
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Port for product information from Catalog context.
 */
public interface ProductCatalogPort {

    /**
     * Gets product details for invoicing.
     */
    CompletionStage<ProductDetails> getProductDetails(UUID productId);

    record ProductDetails(
        UUID productId,
        String name,
        String sku,
        String description,
        String unitPrice,
        String currencyCode,
        String taxCode,
        boolean taxable
    ) {}
}