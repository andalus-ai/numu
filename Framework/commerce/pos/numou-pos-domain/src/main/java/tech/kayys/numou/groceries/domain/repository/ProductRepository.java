package tech.kayys.syirkah.groceries.domain.repository;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;
import tech.kayys.syirkah.groceries.domain.model.PosProduct;

import java.util.concurrent.CompletionStage;

/**
 * Repository for the POS (Groceries) product aggregate.
 *
 * <p>This is <b>not</b> the Product foundation repository. It persists
 * POS-local product extensions (shelf life, batch lots, allergens) that
 * reference a product in the Catalog / Product bounded context by
 * {@code catalogProductId}.</p>
 */
public interface ProductRepository extends Repository<PosProduct, ProductId> {

    CompletionStage<PosProduct> findByCatalogProductId(java.util.UUID catalogProductId);

    CompletionStage<java.util.List<PosProduct>> findExpiringProducts(int daysThreshold);

    CompletionStage<java.util.List<PosProduct>> findExpiredProducts();
}
